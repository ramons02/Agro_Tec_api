package com.agroclima.api.business.clima;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.estacao.EstacaoInmet;
import com.agroclima.api.business.estacao.EstacaoInmetRepository;
import com.agroclima.api.business.estacao.MedicaoClimaRepository;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.talhao.Talhao;
import com.agroclima.api.business.talhao.TalhaoRepository;
import com.agroclima.api.core.geo.GeometriaUtil;
import com.agroclima.api.core.security.JwtService;
import com.agroclima.api.support.ClimaIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/** Espelha tests/integration/test_clima_atual_integration.py e test_estacao_mais_proxima_integration.py. */
class ClimaControllerIT extends ClimaIntegrationTestBase {

    private static final double LON = -48.50;
    private static final double LAT = -1.45;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PropriedadeRepository propriedadeRepository;

    @Autowired
    private TalhaoRepository talhaoRepository;

    @Autowired
    private EstacaoInmetRepository estacaoInmetRepository;

    @Autowired
    private MedicaoClimaRepository medicaoClimaRepository;

    @Autowired
    private JwtService jwtService;

    private String token;
    private UUID talhaoId;

    @BeforeEach
    void preparar() {
        // Limpeza de todas as tabelas ja roda no @BeforeEach da superclasse (IntegrationTestBase).
        Usuario usuario = usuarioRepository.save(
                new Usuario("Dono Clima", "dono-clima@teste.com", "hash-irrelevante", Papel.PRODUTOR_RURAL));
        token = jwtService.criarToken(usuario.getId(), usuario.getPapel()).token();

        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda Clima", "Belém", usuario.getId(), null));
        Talhao talhao = talhaoRepository.save(new Talhao(
                propriedade.getId(), "Talhão Clima", GeometriaUtil.normalizarParaMultiPolygon(quadrado(LON, LAT, 0.01)), 100.0));
        talhaoId = talhao.getId();

        Point posicao = GeometriaUtil.FACTORY_4326.createPoint(new Coordinate(LON + 0.02, LAT + 0.02));
        estacaoInmetRepository.save(new EstacaoInmet("A001", "Estação Teste A", "PA", posicao));
    }

    private Polygon quadrado(double lon0, double lat0, double lado) {
        return GeometriaUtil.FACTORY_4326.createPolygon(new Coordinate[] {
                new Coordinate(lon0, lat0),
                new Coordinate(lon0 + lado, lat0),
                new Coordinate(lon0 + lado, lat0 + lado),
                new Coordinate(lon0, lat0 + lado),
                new Coordinate(lon0, lat0),
        });
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private void stubInmetComLeitura() {
        // Precisa ser "agora" (UTC) -- RN008 considera expirado apos 30min, e um horario
        // fixo no passado faria a leitura nascer stale dependendo de quando o teste roda.
        java.time.ZonedDateTime agoraUtc = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
        String data = agoraUtc.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String hora = agoraUtc.format(java.time.format.DateTimeFormatter.ofPattern("HHmm"));
        String corpo = "["
                + "{\"DT_MEDICAO\":\"" + data + "\",\"HR_MEDICAO\":\"" + hora + "\",\"CHUVA\":\"2.5\","
                + "\"TEM_INS\":\"29.4\",\"UMD_INS\":\"65\",\"VEN_VEL\":\"1.5\",\"VEN_RAJA\":\"3.0\"}"
                + "]";
        INMET_MOCK.stubFor(get(urlPathMatching("/estacao/dados/.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(corpo)));
    }

    private void stubInmetIndisponivel() {
        INMET_MOCK.stubFor(get(urlPathMatching("/estacao/dados/.*")).willReturn(aResponse().withStatus(500)));
    }

    private void stubOpenMeteoComPrevisao() {
        String corpo = "{"
                + "\"hourly\":{\"wind_speed_10m\":[12.0],\"wind_speed_100m\":[20.0],"
                + "\"soil_moisture_0_to_7cm\":[0.3],\"soil_moisture_7_to_28cm\":[0.35]},"
                + "\"daily\":{\"et0_fao_evapotranspiration\":[4.2],\"precipitation_sum\":[0.0]}"
                + "}";
        OPENMETEO_MOCK.stubFor(get(urlPathMatching("/v1/forecast.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(corpo)));
    }

    private void stubOpenMeteoIndisponivel() {
        OPENMETEO_MOCK.stubFor(get(urlPathMatching("/v1/forecast.*")).willReturn(aResponse().withStatus(500)));
    }

    @Test
    void climaAtualIngereDoInmetQuandoNaoHaMedicaoERetornaAoVivo() {
        stubInmetComLeitura();

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/clima/atual?talhao_id=" + talhaoId, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = (Map<String, Object>) resposta.getBody().get("dados");
        assertThat(dados.get("fonte_dados")).isEqualTo("AO_VIVO");
        assertThat(((Number) dados.get("chuva_mm")).doubleValue()).isEqualTo(2.5);
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-cache");
    }

    @Test
    void climaAtualUsaFallbackOpenMeteoQuandoInmetFalha() {
        stubInmetIndisponivel();
        stubOpenMeteoComPrevisao();

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/clima/atual?talhao_id=" + talhaoId, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = (Map<String, Object>) resposta.getBody().get("dados");
        // fallback bem-sucedido conta como leitura fresca -- AO_VIVO, igual ao Python.
        assertThat(dados.get("fonte_dados")).isEqualTo("AO_VIVO");
    }

    @Test
    void climaAtualRetorna404QuandoInmetEOpenMeteoFalhamSemMedicaoPrevia() {
        stubInmetIndisponivel();
        stubOpenMeteoIndisponivel();

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/clima/atual?talhao_id=" + talhaoId, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void climaAtualComTalhaoInexistenteRetorna404() {
        stubInmetComLeitura();
        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/clima/atual?talhao_id=" + UUID.randomUUID(), HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void estacaoMaisProximaRetornaEstacaoCadastrada() {
        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/talhoes/" + talhaoId + "/estacao-mais-proxima", HttpMethod.GET,
                new HttpEntity<>(headers()), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = (Map<String, Object>) resposta.getBody().get("dados");
        var estacoes = (java.util.List<Map<String, Object>>) dados.get("estacoes");
        assertThat(estacoes).hasSize(1);
        assertThat(estacoes.get(0).get("estacao_codigo")).isEqualTo("A001");
        assertThat(estacoes.get(0).get("nome")).isEqualTo("Estação Teste A");
    }

    @Test
    void pulverizacaoClassificaFavoravelComVentoDentroDaFaixa() {
        stubInmetComLeitura(); // VEN_VEL=1.5 m/s -> 5.4 km/h, dentro de 3-10 -> favoravel

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/talhoes/" + talhaoId + "/pulverizacao", HttpMethod.GET, new HttpEntity<>(headers()), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = (Map<String, Object>) resposta.getBody().get("dados");
        assertThat(dados.get("classificacao")).isEqualTo("FAVORAVEL");
        assertThat((java.util.List<?>) dados.get("motivos_bloqueio")).isEmpty();
    }
}
