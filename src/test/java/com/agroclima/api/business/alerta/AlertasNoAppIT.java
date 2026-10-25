package com.agroclima.api.business.alerta;

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
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Alertas dentro do app (sino), com o Telegram desligado (padrao): o job grava o aviso para quem tem acesso ao talhão. */
class AlertasNoAppIT extends ClimaIntegrationTestBase {

    @Autowired private TestRestTemplate rest;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PropriedadeRepository propriedadeRepository;
    @Autowired private TalhaoRepository talhaoRepository;
    @Autowired private AlertaPulverizacaoService alertaService;
    @Autowired private AlertaRepository alertaRepository;
    @Autowired private JwtService jwtService;

    private Usuario novoUsuario(String email) {
        return usuarioRepository.save(new Usuario("Pessoa", email, "hash-irrelevante", Papel.PRODUTOR_RURAL));
    }

    private HttpEntity<Void> como(Usuario usuario) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtService.criarToken(usuario.getId(), usuario.getPapel()).token());
        return new HttpEntity<>(headers);
    }

    /** Rajada de 21,6 km/h: acima do limite seguro, dispara o alerta de rajada sem depender de transição. */
    private Talhao talhaoComRajadaForte(Usuario dono, double deslocamento) {
        double lon = -47.90 + deslocamento;
        double lat = -1.40 + deslocamento;
        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda Sino", "Belém", dono.getId(), null));
        Talhao talhao = talhaoRepository.save(new Talhao(propriedade.getId(), "Talhão Sino",
                GeometriaUtil.normalizarParaMultiPolygon(GeometriaUtil.FACTORY_4326.createPolygon(new Coordinate[] {
                        new Coordinate(lon, lat), new Coordinate(lon + 0.01, lat),
                        new Coordinate(lon + 0.01, lat + 0.01), new Coordinate(lon, lat + 0.01),
                        new Coordinate(lon, lat)})), 50.0));
        stubOpenMeteoCondicaoAtual(28.0, 60.0, 1.0 * 3.6, 6.0 * 3.6, 0.0);
        return talhao;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> listar(Usuario usuario) {
        ResponseEntity<Map> r = rest.exchange("/api/v1/alertas", HttpMethod.GET, como(usuario), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) r.getBody().get("dados");
    }

    @Test
    void jobGravaOAlertaNoSinoDoDonoMesmoSemTelegram() {
        Usuario dono = novoUsuario("sino-dono@teste.com");
        talhaoComRajadaForte(dono, 0.40);

        alertaService.processarTodosTalhoes();

        Map<String, Object> dados = listar(dono);
        assertThat(((Number) dados.get("nao_lidos")).intValue()).isEqualTo(1);
        List<Map<String, Object>> itens = (List<Map<String, Object>>) dados.get("itens");
        assertThat(itens).hasSize(1);
        assertThat(itens.get(0).get("tipo")).isEqualTo("RAJADA_FORTE");
        assertThat((String) itens.get(0).get("mensagem")).contains("rajada de 22 km/h");
    }

    @Test
    void rajadaNaoRepeteODentroDaJanelaDeSilencio() {
        Usuario dono = novoUsuario("sino-silencio@teste.com");
        talhaoComRajadaForte(dono, 0.50);

        alertaService.processarTodosTalhoes();
        alertaService.processarTodosTalhoes();

        assertThat(alertaRepository.findTop50ByUsuarioIdOrderByCriadoEmDesc(dono.getId())).hasSize(1);
    }

    @Test
    void contaSuspensaNaoRecebeAlertaNoSino() {
        Usuario dono = novoUsuario("sino-suspenso@teste.com");
        dono.suspender("atraso", java.time.Instant.now());
        usuarioRepository.save(dono);
        talhaoComRajadaForte(dono, 0.60);

        alertaService.processarTodosTalhoes();

        assertThat(alertaRepository.findTop50ByUsuarioIdOrderByCriadoEmDesc(dono.getId())).isEmpty();
    }

    @Test
    void marcarComoLidoZeraOContador() {
        Usuario dono = novoUsuario("sino-lido@teste.com");
        talhaoComRajadaForte(dono, 0.70);
        alertaService.processarTodosTalhoes();
        Alerta alerta = alertaRepository.findTop50ByUsuarioIdOrderByCriadoEmDesc(dono.getId()).get(0);

        ResponseEntity<Void> r = rest.exchange("/api/v1/alertas/" + alerta.getId() + "/lido", HttpMethod.POST, como(dono), Void.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(((Number) listar(dono).get("nao_lidos")).intValue()).isZero();
    }

    @Test
    void marcarTodosComoLidos() {
        Usuario dono = novoUsuario("sino-todos@teste.com");
        talhaoComRajadaForte(dono, 0.80);
        alertaService.processarTodosTalhoes();

        ResponseEntity<Void> r = rest.exchange("/api/v1/alertas/lidos", HttpMethod.POST, como(dono), Void.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(((Number) listar(dono).get("nao_lidos")).intValue()).isZero();
    }

    @Test
    void outraContaNaoVeNemMarcaOAlertaDeQuemNaoEla() {
        Usuario dono = novoUsuario("sino-dono2@teste.com");
        Usuario intruso = novoUsuario("sino-intruso@teste.com");
        talhaoComRajadaForte(dono, 0.90);
        alertaService.processarTodosTalhoes();
        Alerta alerta = alertaRepository.findTop50ByUsuarioIdOrderByCriadoEmDesc(dono.getId()).get(0);

        assertThat((List<?>) listar(intruso).get("itens")).isEmpty();
        ResponseEntity<String> r = rest.exchange("/api/v1/alertas/" + alerta.getId() + "/lido", HttpMethod.POST, como(intruso), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(alertaRepository.findById(alerta.getId()).orElseThrow().getLidoEm()).isNull();
    }

    @Test
    void telegramDesligadoRespondeNaoEncontrado() {
        Usuario dono = novoUsuario("sino-telegram@teste.com");

        ResponseEntity<String> r = rest.exchange("/api/v1/telegram/vincular-link", HttpMethod.POST, como(dono), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
