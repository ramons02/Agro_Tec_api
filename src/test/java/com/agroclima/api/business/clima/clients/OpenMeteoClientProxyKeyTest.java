package com.agroclima.api.business.clima.clients;

import com.agroclima.api.core.config.AppProperties;
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
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

class OpenMeteoClientProxyKeyTest {

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
    void preparar() {
        MOCK.resetAll();
        String corpo = "{"
                + "\"hourly\":{\"wind_speed_10m\":[12.0],\"wind_speed_100m\":[20.0],"
                + "\"soil_moisture_0_to_7cm\":[0.3],\"soil_moisture_7_to_28cm\":[0.35]},"
                + "\"daily\":{\"et0_fao_evapotranspiration\":[4.2],\"precipitation_sum\":[0.0]}"
                + "}";
        MOCK.stubFor(get(urlPathMatching("/v1/forecast.*")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(corpo)));
    }

    private OpenMeteoClient cliente(String proxyKey) {
        AppProperties props = new AppProperties(
                null, null, null, null, null, new AppProperties.Openmeteo(MOCK.baseUrl(), proxyKey), null, null);
        return new OpenMeteoClient(props);
    }

    @Test
    void comChaveConfiguradaEnviaOHeaderXProxyKey() {
        cliente("chave-do-proxy").obterPrevisao(-1.45, -48.5);

        MOCK.verify(getRequestedFor(urlPathMatching("/v1/forecast.*")).withHeader("X-Proxy-Key", equalTo("chave-do-proxy")));
    }

    @Test
    void semChaveNaoEnviaOHeader() {
        cliente("").obterPrevisao(-1.45, -48.5);
        cliente(null).obterPrevisao(-1.46, -48.5);

        MOCK.verify(2, getRequestedFor(urlPathMatching("/v1/forecast.*")).withoutHeader("X-Proxy-Key"));
    }
}
