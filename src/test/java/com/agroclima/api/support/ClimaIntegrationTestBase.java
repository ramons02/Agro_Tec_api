package com.agroclima.api.support;

import com.agroclima.api.business.clima.clients.MetNorwayClient;
import com.agroclima.api.business.clima.clients.OpenMeteoClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base pra testes que precisam mockar INMET/Open-Meteo via WireMock (em vez de chamar a API real). */
public abstract class ClimaIntegrationTestBase extends IntegrationTestBase {

    protected static final WireMockServer INMET_MOCK =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    protected static final WireMockServer OPENMETEO_MOCK =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        INMET_MOCK.start();
        OPENMETEO_MOCK.start();
    }

    @DynamicPropertySource
    static void wiremockProperties(DynamicPropertyRegistry registry) {
        registry.add("app.inmet.base-url", INMET_MOCK::baseUrl);
        registry.add("app.openmeteo.base-url", OPENMETEO_MOCK::baseUrl);
        // Sem rede real nos testes: caminhos do MET Norway nao stubados voltam 404 do WireMock.
        registry.add("app.metno.base-url", OPENMETEO_MOCK::baseUrl);
    }

    @Autowired
    private OpenMeteoClient openMeteoClient;

    @Autowired
    private MetNorwayClient metNorwayClient;

    @BeforeEach
    void resetarMocksHttp() {
        INMET_MOCK.resetAll();
        OPENMETEO_MOCK.resetAll();
        // Caches dos clientes sao de singleton: sem isso um teste herda a previsao guardada por outro.
        openMeteoClient.limparCaches();
        metNorwayClient.limparCache();
    }
}
