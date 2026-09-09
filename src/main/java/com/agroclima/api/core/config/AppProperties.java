package com.agroclima.api.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Espelha app/core/config.py (pydantic-settings). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt, Cors cors, String frontendBaseUrl, Smtp smtp, Inmet inmet, Openmeteo openmeteo,
        Telegram telegram) {

    public record Jwt(String secret, String algorithm, int expirationHours) {}

    public record Cors(List<String> origins, String originRegex) {}

    public record Smtp(String host, int port, String user, String password, String from) {}

    /** botToken vazio = TelegramConfig cai no fallback de log (feature 017, mesmo padrao de SMTP).
     * baseUrl override-avel em teste (WireMock), mesmo padrao de Inmet/Openmeteo. */
    public record Telegram(String botToken, String botUsername, String baseUrl) {}

    /** baseUrl override-avel em teste (WireMock) -- em producao aponta pra API real do INMET. */
    public record Inmet(String baseUrl) {}

    /** baseUrl override-avel em teste (WireMock) -- em producao aponta pra API real do Open-Meteo. */
    public record Openmeteo(String baseUrl) {}
}
