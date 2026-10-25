package com.agroclima.api.business.push;

import com.agroclima.api.business.alerta.AlertaRepository;
import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.talhao.Talhao;
import com.agroclima.api.business.talhao.TalhaoRepository;
import com.agroclima.api.business.telegram.AlertaPulverizacaoService;
import com.agroclima.api.core.geo.GeometriaUtil;
import com.agroclima.api.core.security.JwtService;
import com.agroclima.api.support.ClimaIntegrationTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/** Notificacao push com o app fechado: assinatura, envio quando o alerta nasce, limpeza de aparelho extinto. */
class PushIT extends ClimaIntegrationTestBase {

    private static final WireMockServer SERVICO_DE_PUSH = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        SERVICO_DE_PUSH.start();
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("app.push.permitir-endpoint-local", () -> "true");
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PropriedadeRepository propriedadeRepository;
    @Autowired private TalhaoRepository talhaoRepository;
    @Autowired private AlertaPulverizacaoService alertaService;
    @Autowired private AlertaRepository alertaRepository;
    @Autowired private AssinaturaPushRepository assinaturaRepository;
    @Autowired private ChavesVapid chavesVapid;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;

    @BeforeEach
    void limpar() {
        SERVICO_DE_PUSH.resetAll();
    }

    private Usuario novoUsuario(String email) {
        return usuarioRepository.save(new Usuario("Pessoa", email, "hash-irrelevante", Papel.PRODUTOR_RURAL));
    }

    private HttpHeaders cabecalhos(Usuario usuario) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtService.criarToken(usuario.getId(), usuario.getPapel()).token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /** Assinatura como o navegador a entrega: chave P-256 do aparelho e segredo de autenticacao de 16 bytes. */
    private Map<String, Object> assinatura(String endpoint) {
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        return Map.of("endpoint", endpoint,
                "keys", Map.of("p256dh", ChavesVapid.gerar().publica(),
                        "auth", Base64.getUrlEncoder().withoutPadding().encodeToString(auth)));
    }

    private ResponseEntity<String> assinar(Usuario usuario, Map<String, Object> corpo) {
        return rest.exchange("/api/v1/push/assinaturas", HttpMethod.POST, new HttpEntity<>(corpo, cabecalhos(usuario)), String.class);
    }

    private String endpointLocal(String caminho) {
        return SERVICO_DE_PUSH.baseUrl() + caminho;
    }

    private void talhaoComRajadaForte(Usuario dono, double deslocamento) {
        double lon = -47.90 + deslocamento;
        double lat = -1.40 + deslocamento;
        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda Push", "Belém", dono.getId(), null));
        talhaoRepository.save(new Talhao(propriedade.getId(), "Talhão Push",
                GeometriaUtil.normalizarParaMultiPolygon(GeometriaUtil.FACTORY_4326.createPolygon(new Coordinate[] {
                        new Coordinate(lon, lat), new Coordinate(lon + 0.01, lat),
                        new Coordinate(lon + 0.01, lat + 0.01), new Coordinate(lon, lat + 0.01),
                        new Coordinate(lon, lat)})), 50.0));
        stubOpenMeteoCondicaoAtual(28.0, 60.0, 1.0 * 3.6, 6.0 * 3.6, 0.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void chavePublicaVemDoServidorEPersiste() {
        Usuario u = novoUsuario("push-chave@teste.com");

        ResponseEntity<Map> r = rest.exchange("/api/v1/push/chave", HttpMethod.GET, new HttpEntity<>(cabecalhos(u)), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        String publica = (String) ((Map<String, Object>) r.getBody().get("dados")).get("chave_publica");
        assertThat(Base64.getUrlDecoder().decode(publica)).hasSize(65).startsWith((byte) 0x04);
        assertThat(publica).isEqualTo(chavesVapid.obter().publica());
        assertThat(jdbc.queryForObject("select count(*) from config_push", Integer.class)).isEqualTo(1);
    }

    @Test
    void alertaNovoMandaPushParaOAparelhoDaPessoa() {
        SERVICO_DE_PUSH.stubFor(post(urlPathEqualTo("/push/aparelho1")).willReturn(aResponse().withStatus(201)));
        Usuario dono = novoUsuario("push-envio@teste.com");
        assertThat(assinar(dono, assinatura(endpointLocal("/push/aparelho1"))).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        talhaoComRajadaForte(dono, 0.40);

        alertaService.processarTodosTalhoes();

        SERVICO_DE_PUSH.verify(1, postRequestedFor(urlPathEqualTo("/push/aparelho1"))
                .withHeader("Content-Encoding", equalTo("aes128gcm"))
                .withHeader("TTL", equalTo("10800"))
                .withHeader("Authorization", matching("vapid t=.+, k=.+")));
        assertThat(alertaRepository.findTop50ByUsuarioIdOrderByCriadoEmDesc(dono.getId())).hasSize(1);
    }

    @Test
    void aparelhoExtintoPerdeAAssinatura() {
        SERVICO_DE_PUSH.stubFor(post(urlPathEqualTo("/push/morto")).willReturn(aResponse().withStatus(410)));
        Usuario dono = novoUsuario("push-morto@teste.com");
        assinar(dono, assinatura(endpointLocal("/push/morto")));
        talhaoComRajadaForte(dono, 0.50);

        alertaService.processarTodosTalhoes();

        assertThat(assinaturaRepository.findByUsuarioId(dono.getId())).isEmpty();
    }

    @Test
    void falhaDoServicoDePushNaoDerrubaOAlertaNoSino() {
        SERVICO_DE_PUSH.stubFor(post(urlPathEqualTo("/push/erro")).willReturn(aResponse().withStatus(500)));
        Usuario dono = novoUsuario("push-erro@teste.com");
        assinar(dono, assinatura(endpointLocal("/push/erro")));
        talhaoComRajadaForte(dono, 0.60);

        alertaService.processarTodosTalhoes();

        assertThat(alertaRepository.findTop50ByUsuarioIdOrderByCriadoEmDesc(dono.getId())).hasSize(1);
        assertThat(assinaturaRepository.findByUsuarioId(dono.getId())).hasSize(1); // 500 nao apaga a assinatura
    }

    @Test
    void semAssinaturaNaoHaChamadaDePush() {
        Usuario dono = novoUsuario("push-sem@teste.com");
        talhaoComRajadaForte(dono, 0.70);

        alertaService.processarTodosTalhoes();

        assertThat(SERVICO_DE_PUSH.getAllServeEvents()).isEmpty();
    }

    @Test
    void recusaEnderecoQueNaoEServicoDePush() {
        Usuario u = novoUsuario("push-ssrf@teste.com");

        assertThat(assinar(u, assinatura("https://169.254.169.254/latest/meta-data")).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(assinar(u, assinatura("http://evil.example.com/x")).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(assinaturaRepository.findByUsuarioId(u.getId())).isEmpty();
    }

    @Test
    void mesmoAparelhoComOutraContaPassaParaANovaConta() {
        Usuario a = novoUsuario("push-a@teste.com");
        Usuario b = novoUsuario("push-b@teste.com");
        String endpoint = endpointLocal("/push/compartilhado");
        assinar(a, assinatura(endpoint));

        assinar(b, assinatura(endpoint));

        assertThat(assinaturaRepository.findByUsuarioId(a.getId())).isEmpty();
        assertThat(assinaturaRepository.findByUsuarioId(b.getId())).hasSize(1);
    }

    @Test
    void removerSoApagaAAssinaturaDaPropriaConta() {
        Usuario dono = novoUsuario("push-rem1@teste.com");
        Usuario outro = novoUsuario("push-rem2@teste.com");
        String endpoint = endpointLocal("/push/removivel");
        assinar(dono, assinatura(endpoint));

        rest.exchange("/api/v1/push/assinaturas/remover", HttpMethod.POST,
                new HttpEntity<>(Map.of("endpoint", endpoint), cabecalhos(outro)), String.class);
        assertThat(assinaturaRepository.findByUsuarioId(dono.getId())).hasSize(1);

        ResponseEntity<String> r = rest.exchange("/api/v1/push/assinaturas/remover", HttpMethod.POST,
                new HttpEntity<>(Map.of("endpoint", endpoint), cabecalhos(dono)), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(assinaturaRepository.findByUsuarioId(dono.getId())).isEmpty();
    }
}
