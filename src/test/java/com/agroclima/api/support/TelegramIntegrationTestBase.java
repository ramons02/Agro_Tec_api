package com.agroclima.api.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base pra testes de alertas via Telegram (feature 017) -- extende o mock de INMET/Open-Meteo
 * (a classificacao de pulverizacao depende deles) e adiciona o mock da Telegram Bot API, com
 * um bot-token/bot-username fake pra forcar HttpTelegramClient em vez de LogTelegramClient. */
public abstract class TelegramIntegrationTestBase extends ClimaIntegrationTestBase {

    protected static final WireMockServer TELEGRAM_MOCK =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        TELEGRAM_MOCK.start();
    }

    @DynamicPropertySource
    static void wiremockTelegramProperties(DynamicPropertyRegistry registry) {
        registry.add("app.telegram.base-url", TELEGRAM_MOCK::baseUrl);
        registry.add("app.telegram.bot-token", () -> "token-de-teste");
        registry.add("app.telegram.bot-username", () -> "AgroClimaParaTesteBot");
    }

    @BeforeEach
    void resetarMockTelegram() {
        TELEGRAM_MOCK.resetAll();
    }
}
