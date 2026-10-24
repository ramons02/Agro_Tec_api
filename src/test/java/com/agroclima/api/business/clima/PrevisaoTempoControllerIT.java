package com.agroclima.api.business.clima;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.core.security.JwtService;
import com.agroclima.api.support.ClimaIntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/** Rota GET /previsao (spec 023, T009 e T011): campos, validacao, cache, reserva e erro quando as duas fontes caem. */
class PrevisaoTempoControllerIT extends ClimaIntegrationTestBase {

    private static final String METNO = "/weatherapi/locationforecast/2.0/complete";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    private String token;

    @BeforeEach
    void preparar() {
        Usuario usuario = usuarioRepository.save(
                new Usuario("Quem Consulta", "previsao@teste.com", "hash-irrelevante", Papel.PRODUTOR_RURAL));
        token = jwtService.criarToken(usuario.getId(), usuario.getPapel()).token();
    }

    private ResponseEntity<String> consultar(String query, String tokenUsado) {
        HttpHeaders headers = new HttpHeaders();
        if (tokenUsado != null) {
            headers.setBearerAuth(tokenUsado);
        }
        return restTemplate.exchange("/api/v1/previsao" + query, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private JsonNode corpo(ResponseEntity<String> resposta) throws Exception {
        return JSON.readTree(resposta.getBody());
    }

    private void stubOpenMeteo10Dias(boolean semRajada) {
        StringBuilder datas = new StringBuilder();
        StringBuilder valores = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            datas.append(i > 0 ? "," : "").append("\"").append(LocalDate.of(2026, 10, 24).plusDays(i)).append("\"");
            valores.append(i > 0 ? "," : "").append(20 + i).append(".5");
        }
        String rajada = semRajada ? "" : ",\"wind_gusts_10m_max\":[" + valores + "]";
        String json = "{\"daily\":{\"time\":[" + datas + "],\"temperature_2m_min\":[" + valores + "],"
                + "\"temperature_2m_max\":[" + valores + "],\"precipitation_sum\":[" + valores + "],"
                + "\"precipitation_probability_max\":[" + valores + "],\"wind_speed_10m_max\":[" + valores + "]"
                + rajada + "}}";
        OPENMETEO_MOCK.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(json)));
    }

    private void stubMetNorway3Pontos() {
        String json = "{\"properties\":{\"timeseries\":["
                + ponto("2026-10-24T00:00:00Z", 24.0, 2.0, 1.0) + ","
                + ponto("2026-10-24T06:00:00Z", 30.0, 3.0, 2.0) + ","
                + ponto("2026-10-24T12:00:00Z", 28.0, 1.0, 0.5) + "]}}";
        OPENMETEO_MOCK.stubFor(get(urlPathEqualTo(METNO)).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(json)));
    }

    private static String ponto(String horario, double temperaturaC, double ventoMs, double chuvaMm) {
        return "{\"time\":\"" + horario + "\",\"data\":{\"instant\":{\"details\":{\"air_temperature\":" + temperaturaC
                + ",\"wind_speed\":" + ventoMs + "}},\"next_6_hours\":{\"details\":{\"precipitation_amount\":"
                + chuvaMm + "}}}}";
    }

    @Test
    void devolveFonteEOsDezDiasComOsCamposEsperados() throws Exception {
        stubOpenMeteo10Dias(false);

        ResponseEntity<String> resposta = consultar("?lat=-1.45&lon=-48.5", token);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode dados = corpo(resposta).path("dados");
        assertThat(dados.path("fonte").asText()).isEqualTo("OPEN_METEO");
        assertThat(dados.path("dias")).hasSize(10);
        JsonNode primeiro = dados.path("dias").get(0);
        assertThat(primeiro.path("data").asText()).isEqualTo("2026-10-24");
        assertThat(primeiro.path("temperatura_min_c").asDouble()).isEqualTo(20.5);
        assertThat(primeiro.path("temperatura_max_c").asDouble()).isEqualTo(20.5);
        assertThat(primeiro.path("precipitacao_prevista_mm").asDouble()).isEqualTo(20.5);
        assertThat(primeiro.path("probabilidade_chuva_pct").asDouble()).isEqualTo(20.5);
        assertThat(primeiro.path("vento_max_kmh").asDouble()).isEqualTo(20.5);
        assertThat(primeiro.path("rajada_max_kmh").asDouble()).isEqualTo(20.5);
    }

    @Test
    void campoQueAFonteNaoEntregouChegaVazioENuncaZero() throws Exception {
        stubOpenMeteo10Dias(true);

        JsonNode dia = corpo(consultar("?lat=-1.45&lon=-48.5", token)).path("dados").path("dias").get(0);

        assertThat(dia.has("rajada_max_kmh")).isTrue();
        assertThat(dia.path("rajada_max_kmh").isNull()).isTrue();
        assertThat(dia.path("vento_max_kmh").asDouble()).isEqualTo(20.5);
    }

    @Test
    void fonteForaMasReservaNoArDevolveFonteMetNorwayComCamposAusentes() throws Exception {
        stubOpenMeteoIndisponivel();
        stubMetNorway3Pontos();

        ResponseEntity<String> resposta = consultar("?lat=-1.45&lon=-48.5", token);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode dados = corpo(resposta).path("dados");
        assertThat(dados.path("fonte").asText()).isEqualTo("MET_NORWAY");
        assertThat(dados.path("dias")).hasSize(1);
        JsonNode dia = dados.path("dias").get(0);
        assertThat(dia.path("temperatura_min_c").asDouble()).isEqualTo(24.0);
        assertThat(dia.path("temperatura_max_c").asDouble()).isEqualTo(30.0);
        assertThat(dia.path("precipitacao_prevista_mm").asDouble()).isEqualTo(3.5);
        assertThat(dia.path("vento_max_kmh").asDouble()).isEqualTo(10.8);
        assertThat(dia.path("rajada_max_kmh").isNull()).isTrue();
        assertThat(dia.path("probabilidade_chuva_pct").isNull()).isTrue();
    }

    @Test
    void asDuasFontesForaResponde503ComAMensagemDeIndisponibilidade() throws Exception {
        stubOpenMeteoIndisponivel();
        OPENMETEO_MOCK.stubFor(get(urlPathEqualTo(METNO)).willReturn(aResponse().withStatus(500)));

        ResponseEntity<String> resposta = consultar("?lat=-1.45&lon=-48.5", token);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(corpo(resposta).path("mensagem").asText())
                .isEqualTo("Previsão do tempo indisponível no momento. Tente novamente em instantes.");
    }

    @Test
    void latitudeELongitudeForaDaFaixaRespondem422SemConsultarAFonte() {
        stubOpenMeteo10Dias(false);

        assertThat(consultar("?lat=200&lon=-48.5", token).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(consultar("?lat=-1.45&lon=-500", token).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(consultar("?lat=-90.01&lon=0", token).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(consultar("?lat=NaN&lon=0", token).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        OPENMETEO_MOCK.verify(exactly(0), getRequestedFor(urlPathEqualTo("/v1/forecast")));
        OPENMETEO_MOCK.verify(exactly(0), getRequestedFor(urlPathEqualTo(METNO)));
    }

    @Test
    void coordenadaNoLimiteDaFaixaEForaDoParaSaoAtendidas() {
        stubOpenMeteo10Dias(false);

        assertThat(consultar("?lat=-90&lon=180", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(consultar("?lat=48.85&lon=2.35", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void parametroAusenteOuNaoNumericoResponde400() {
        assertThat(consultar("?lat=-1.45", token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(consultar("?lat=abc&lon=1", token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void semTokenResponde401() {
        assertThat(consultar("?lat=-1.45&lon=-48.5", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void segundaConsultaDaMesmaCoordenadaNaoChamaAFonteDeNovo() {
        stubOpenMeteo10Dias(false);

        consultar("?lat=-1.4501&lon=-48.5001", token);
        consultar("?lat=-1.4502&lon=-48.5002", token);

        OPENMETEO_MOCK.verify(exactly(1), getRequestedFor(urlPathEqualTo("/v1/forecast")));
    }
}
