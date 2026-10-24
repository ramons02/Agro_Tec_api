package com.agroclima.api.support;

import com.agroclima.api.business.clima.ClimaTempoRealService;
import com.agroclima.api.business.clima.clients.MetNorwayClient;
import com.agroclima.api.business.clima.clients.OpenMeteoClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Base pra testes que dependem de clima: um WireMock faz as vezes do Open-Meteo e do MET Norway
 * (caminhos diferentes no mesmo servidor), entao nenhum teste toca a rede real. Caminho nao
 * stubado volta 404 do WireMock.
 */
public abstract class ClimaIntegrationTestBase extends IntegrationTestBase {

    protected static final WireMockServer OPENMETEO_MOCK =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        OPENMETEO_MOCK.start();
    }

    @DynamicPropertySource
    static void wiremockProperties(DynamicPropertyRegistry registry) {
        registry.add("app.openmeteo.base-url", OPENMETEO_MOCK::baseUrl);
        registry.add("app.metno.base-url", OPENMETEO_MOCK::baseUrl);
    }

    @Autowired
    private OpenMeteoClient openMeteoClient;

    @Autowired
    private MetNorwayClient metNorwayClient;

    @Autowired
    private ClimaTempoRealService climaTempoRealService;

    /** Resposta "current" do Open-Meteo para qualquer coordenada (temperatura em C, vento/rajada em km/h). */
    protected void stubOpenMeteoCondicaoAtual(
            double temperaturaC, double umidadePct, double ventoKmh, double rajadaKmh, double chuvaMm) {
        String corpo = "{\"current\":{\"time\":\"2026-10-24T19:00\",\"interval\":900,"
                + "\"temperature_2m\":" + temperaturaC + ",\"relative_humidity_2m\":" + umidadePct + ","
                + "\"precipitation\":" + chuvaMm + ",\"wind_speed_10m\":" + ventoKmh + ","
                + "\"wind_gusts_10m\":" + rajadaKmh + "}}";
        OPENMETEO_MOCK.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(corpo)));
    }

    protected void stubOpenMeteoIndisponivel() {
        OPENMETEO_MOCK.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(aResponse().withStatus(500)));
    }

    /** Resposta do MET Norway (vento em m/s, sem rajada) para qualquer coordenada. */
    protected void stubMetNorwayCondicao(double temperaturaC, double umidadePct, double ventoMs, double chuvaMm) {
        String corpo = "{\"properties\":{\"timeseries\":[{\"time\":\"2026-10-24T19:00:00Z\",\"data\":{"
                + "\"instant\":{\"details\":{\"air_temperature\":" + temperaturaC + ",\"relative_humidity\":"
                + umidadePct + ",\"wind_speed\":" + ventoMs + "}},"
                + "\"next_1_hours\":{\"details\":{\"precipitation_amount\":" + chuvaMm + "}}}}]}}";
        OPENMETEO_MOCK.stubFor(get(urlPathEqualTo("/weatherapi/locationforecast/2.0/complete")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(corpo)));
    }

    @BeforeEach
    void resetarMocksHttp() {
        OPENMETEO_MOCK.resetAll();
        // Caches dos clientes e do servico sao de singleton: sem isso um teste herda a leitura de outro.
        openMeteoClient.limparCaches();
        metNorwayClient.limparCache();
        climaTempoRealService.limparCache();
    }
}
