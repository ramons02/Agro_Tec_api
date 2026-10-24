package com.agroclima.api.business.clima.clients;

import com.agroclima.api.core.config.AppProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Condicao atual e dados diarios do Open-Meteo: o que substitui as estacoes do INMET. */
class OpenMeteoClientDadosTest {

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

    private OpenMeteoClient cliente() {
        return new OpenMeteoClient(new AppProperties(
                null, null, null, null, new AppProperties.Openmeteo(MOCK.baseUrl(), ""), null));
    }

    private void responder(String json) {
        MOCK.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(json)));
    }

    @Test
    void condicaoAtualLeOsCamposCurrentEOHorarioDoProvedor() {
        responder("{\"current\":{\"time\":\"2026-10-24T19:00\",\"temperature_2m\":29.9,\"relative_humidity_2m\":70,"
                + "\"precipitation\":0.4,\"wind_speed_10m\":13.7,\"wind_gusts_10m\":35.3}}");

        CondicaoAtual c = cliente().obterCondicaoAtual(-1.445, -48.495);

        assertEquals(29.9, c.temperaturaC());
        assertEquals(70.0, c.umidadePct());
        assertEquals(13.7, c.ventoKmh());
        assertEquals(35.3, c.rajadaKmh());
        assertEquals(0.4, c.precipitacaoMm());
        assertEquals(Instant.parse("2026-10-24T19:00:00Z"), c.obtidoEmUtc());
        MOCK.verify(getRequestedFor(urlPathEqualTo("/v1/forecast"))
                .withQueryParam("current", equalTo("temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m,wind_gusts_10m"))
                .withQueryParam("timezone", equalTo("UTC")));
    }

    @Test
    void condicaoAtualSemRajadaOuChuvaDevolveNulosEMantemOResto() {
        responder("{\"current\":{\"time\":\"2026-10-24T19:00\",\"temperature_2m\":20,\"relative_humidity_2m\":50,\"wind_speed_10m\":3}}");

        CondicaoAtual c = cliente().obterCondicaoAtual(0.1, 0.1);

        assertNull(c.rajadaKmh());
        assertNull(c.precipitacaoMm());
        assertEquals(3.0, c.ventoKmh());
    }

    @Test
    void condicaoAtualSemOsCamposObrigatoriosViraFonteIndisponivel() {
        responder("{}");
        assertThrows(FontePrevisaoIndisponivelException.class, () -> cliente().obterCondicaoAtual(0.2, 0.2));

        responder("{\"current\":{\"temperature_2m\":20}}");
        assertThrows(FontePrevisaoIndisponivelException.class, () -> cliente().obterCondicaoAtual(0.3, 0.3));
    }

    @Test
    void condicaoAtualComErroHttpViraFonteIndisponivel() {
        MOCK.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(aResponse().withStatus(500)));
        assertThrows(FontePrevisaoIndisponivelException.class, () -> cliente().obterCondicaoAtual(0.4, 0.4));
    }

    @Test
    void condicaoAtualDaMesmaCoordenadaVemDoCacheDe10Minutos() {
        responder("{\"current\":{\"time\":\"2026-10-24T19:00\",\"temperature_2m\":20,\"relative_humidity_2m\":50,\"wind_speed_10m\":3}}");
        OpenMeteoClient cliente = cliente();

        cliente.obterCondicaoAtual(0.5, 0.5);
        cliente.obterCondicaoAtual(0.5, 0.5);

        MOCK.verify(1, getRequestedFor(urlPathEqualTo("/v1/forecast")));
    }

    @Test
    void dadosDiariosAchamOsValoresDaDataPedidaNoMeioDaSerie() {
        responder("{\"daily\":{\"time\":[\"2026-10-21\",\"2026-10-22\",\"2026-10-23\"],"
                + "\"precipitation_sum\":[3.2,0.0,7.5],\"et0_fao_evapotranspiration\":[4.1,4.4,3.9]}}");

        DadosDiarios dados = cliente().obterDadosDiarios(-1.445, -48.495, LocalDate.parse("2026-10-23"));

        assertEquals(7.5, dados.precipitacaoMm());
        assertEquals(3.9, dados.et0Mm());
        MOCK.verify(getRequestedFor(urlPathEqualTo("/v1/forecast"))
                .withQueryParam("daily", equalTo("precipitation_sum,et0_fao_evapotranspiration"))
                .withQueryParam("past_days", equalTo("3"))
                .withQueryParam("forecast_days", equalTo("1")));
    }

    @Test
    void dadosDiariosDeDiaForaDaSerieOuComValorNuloVoltamNulosSemAssumirZero() {
        responder("{\"daily\":{\"time\":[\"2026-10-22\",\"2026-10-23\"],"
                + "\"precipitation_sum\":[1.0,null],\"et0_fao_evapotranspiration\":[4.0,null]}}");
        OpenMeteoClient cliente = cliente();

        DadosDiarios foraDaSerie = cliente.obterDadosDiarios(0.6, 0.6, LocalDate.parse("2026-01-01"));
        DadosDiarios comNulo = cliente.obterDadosDiarios(0.6, 0.6, LocalDate.parse("2026-10-23"));

        assertNull(foraDaSerie.precipitacaoMm());
        assertNull(comNulo.precipitacaoMm());
        assertNull(comNulo.et0Mm());
    }

    @Test
    void dadosDiariosSemASerieViramFonteIndisponivel() {
        responder("{}");
        assertThrows(FontePrevisaoIndisponivelException.class,
                () -> cliente().obterDadosDiarios(0.7, 0.7, LocalDate.parse("2026-10-23")));
    }
}
