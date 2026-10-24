package com.agroclima.api.business.clima.clients;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetNorwayClientTest {

    private static final String CAMINHO = "/weatherapi/locationforecast/2.0/complete";
    private static final WireMockServer MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @BeforeAll
    static void iniciar() {
        MOCK.start();
    }

    @AfterAll
    static void parar() {
        MOCK.stop();
    }

    @BeforeEach
    void limpar() {
        MOCK.resetAll();
    }

    private static String corpo(String instant, String proximaHora) {
        return "{\"properties\":{\"timeseries\":[{\"time\":\"2026-10-24T19:00:00Z\",\"data\":{"
                + "\"instant\":{\"details\":" + instant + "}" + proximaHora + "}}]}}";
    }

    private void responder(String json) {
        MOCK.stubFor(get(urlPathEqualTo(CAMINHO)).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(json)));
    }

    @Test
    void lePontosEnviaUserAgentEArredondaCoordenadas() {
        responder(corpo("{\"air_temperature\":29.7,\"relative_humidity\":70.8,\"wind_speed\":5.8}",
                ",\"next_1_hours\":{\"details\":{\"precipitation_amount\":1.2}}"));
        MetNorwayClient cliente = new MetNorwayClient(MOCK.baseUrl(), "AgroTeste/1.0 (teste@exemplo.com)");

        CondicaoAtual c = cliente.obterCondicaoAtual(-1.4512, -48.4987);

        assertEquals(29.7, c.temperaturaC());
        assertEquals(70.8, c.umidadePct());
        assertEquals(20.88, c.ventoKmh(), 0.001);
        assertNull(c.rajadaKmh());
        assertEquals(1.2, c.precipitacaoMm());
        MOCK.verify(getRequestedFor(urlPathEqualTo(CAMINHO))
                .withHeader("User-Agent", equalTo("AgroTeste/1.0 (teste@exemplo.com)"))
                .withQueryParam("lat", equalTo("-1.45"))
                .withQueryParam("lon", equalTo("-48.5")));
    }

    @Test
    void semUserAgentConfiguradoUsaOPadraoIdentificado() {
        responder(corpo("{\"air_temperature\":20,\"relative_humidity\":50,\"wind_speed\":1}", ""));

        new MetNorwayClient(MOCK.baseUrl(), "").obterCondicaoAtual(0.1, 0.1);

        MOCK.verify(getRequestedFor(urlPathEqualTo(CAMINHO))
                .withHeader("User-Agent", equalTo(MetNorwayClient.USER_AGENT_PADRAO)));
    }

    @Test
    void semBlocoDeChuvaDevolveChuvaNula() {
        responder(corpo("{\"air_temperature\":20,\"relative_humidity\":50,\"wind_speed\":1}", ""));

        assertNull(new MetNorwayClient(MOCK.baseUrl(), "").obterCondicaoAtual(0.2, 0.2).precipitacaoMm());
    }

    @Test
    void segundaConsultaDaMesmaCoordenadaVemDoCache() {
        responder(corpo("{\"air_temperature\":20,\"relative_humidity\":50,\"wind_speed\":1}", ""));
        MetNorwayClient cliente = new MetNorwayClient(MOCK.baseUrl(), "");

        cliente.obterCondicaoAtual(0.3, 0.3);
        cliente.obterCondicaoAtual(0.3, 0.3);

        MOCK.verify(1, getRequestedFor(urlPathEqualTo(CAMINHO)));
    }

    @Test
    void erroHttpOuRespostaSemOsCamposViraFonteIndisponivel() {
        MOCK.stubFor(get(urlPathEqualTo(CAMINHO)).willReturn(aResponse().withStatus(503)));
        assertThrows(FontePrevisaoIndisponivelException.class,
                () -> new MetNorwayClient(MOCK.baseUrl(), "").obterCondicaoAtual(0.4, 0.4));

        responder(corpo("{\"air_temperature\":20}", ""));
        assertThrows(FontePrevisaoIndisponivelException.class,
                () -> new MetNorwayClient(MOCK.baseUrl(), "").obterCondicaoAtual(0.5, 0.5));
    }
}
