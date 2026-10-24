package com.agroclima.api.business.clima;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.clima.clients.OpenMeteoClient;
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
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/** Clima atual por talhao (RF033, RN008, RN017): sem estacoes, direto do Open-Meteo no centroide. */
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
    private OpenMeteoClient openMeteoClient;

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

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> consultar() {
        return restTemplate.exchange(
                "/api/v1/clima/atual?talhao_id=" + talhaoId, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dados(ResponseEntity<Map> resposta) {
        return (Map<String, Object>) resposta.getBody().get("dados");
    }

    @Test
    void climaAtualVemDoOpenMeteoNoCentroideDoTalhaoEMarcaPrevisao() {
        stubOpenMeteoCondicaoAtual(29.9, 70.0, 13.7, 35.3, 0.4);

        ResponseEntity<Map> resposta = consultar();

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = dados(resposta);
        assertThat(dados.get("fonte_dados")).isEqualTo("PREVISAO");
        assertThat(((Number) dados.get("temperatura_c")).doubleValue()).isEqualTo(29.9);
        assertThat(((Number) dados.get("umidade_pct")).doubleValue()).isEqualTo(70.0);
        assertThat(((Number) dados.get("vento_kmh")).doubleValue()).isEqualTo(13.7);
        assertThat(((Number) dados.get("rajada_kmh")).doubleValue()).isEqualTo(35.3);
        assertThat(((Number) dados.get("chuva_mm")).doubleValue()).isEqualTo(0.4);
        assertThat(dados.get("medido_em_utc")).isEqualTo("2026-10-24T19:00:00Z");
        assertThat(dados).doesNotContainKey("estacao");
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-cache");
        // O ponto consultado e o centroide do talhao (lado de 0,01 grau), nao uma estacao.
        OPENMETEO_MOCK.verify(getRequestedFor(urlPathEqualTo("/v1/forecast"))
                .withQueryParam("current", matching(".*wind_gusts_10m.*"))
                .withQueryParam("latitude", matching("-1\\.44(5|49).*"))
                .withQueryParam("longitude", matching("-48\\.49(5|49).*")));
    }

    @Test
    void usaMetNorwayQuandoOOpenMeteoFalha() {
        stubOpenMeteoIndisponivel();
        stubMetNorwayCondicao(29.7, 70.8, 5.0, 0.0);

        ResponseEntity<Map> resposta = consultar();

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = dados(resposta);
        assertThat(dados.get("fonte_dados")).isEqualTo("PREVISAO");
        assertThat(((Number) dados.get("temperatura_c")).doubleValue()).isEqualTo(29.7);
        assertThat(((Number) dados.get("vento_kmh")).doubleValue()).isEqualTo(18.0);
        assertThat(dados.get("rajada_kmh")).isNull();
    }

    @Test
    void usaMetNorwayQuandoOOpenMeteoResponde200SemACondicaoAtual() {
        OPENMETEO_MOCK.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{}")));
        stubMetNorwayCondicao(25.0, 80.0, 2.0, 0.0);

        ResponseEntity<Map> resposta = consultar();

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) dados(resposta).get("temperatura_c")).doubleValue()).isEqualTo(25.0);
    }

    @Test
    void retorna404QuandoOsDoisProvedoresFalhamSemLeituraAnterior() {
        stubOpenMeteoIndisponivel();

        ResponseEntity<Map> resposta = consultar();

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void comOsDoisProvedoresForaDoArDevolveALeituraAnteriorComoCacheExpirado() {
        stubOpenMeteoCondicaoAtual(31.0, 55.0, 9.0, 20.0, 0.0);
        assertThat(dados(consultar()).get("fonte_dados")).isEqualTo("PREVISAO");

        // O cache de 10 min do cliente expira e os dois provedores caem.
        openMeteoClient.limparCaches();
        OPENMETEO_MOCK.resetAll();
        stubOpenMeteoIndisponivel();

        ResponseEntity<Map> resposta = consultar();

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = dados(resposta);
        assertThat(dados.get("fonte_dados")).isEqualTo("CACHE_EXPIRADO");
        assertThat(((Number) dados.get("temperatura_c")).doubleValue()).isEqualTo(31.0);
    }

    @Test
    void talhaoInexistenteRetorna404() {
        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/clima/atual?talhao_id=" + UUID.randomUUID(), HttpMethod.GET,
                new HttpEntity<>(headers()), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void semTokenRetorna401() {
        ResponseEntity<Map> resposta = restTemplate.getForEntity(
                "/api/v1/clima/atual?talhao_id=" + talhaoId, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
