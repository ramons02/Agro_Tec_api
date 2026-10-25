package com.agroclima.api.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Espelha app/core/config.py (pydantic-settings). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt, Cors cors, String frontendBaseUrl, Smtp smtp, Openmeteo openmeteo, Telegram telegram) {

    public record Jwt(String secret, String algorithm, int expirationHours) {}

    public record Cors(List<String> origins, String originRegex) {}

    public record Smtp(String host, int port, String user, String password, String from) {}

    /** habilitado = false (padrao): o Telegram esta desligado, os alertas vao so para o sino do app e os
     * endpoints /telegram respondem 404. O codigo fica para o caso de a integracao voltar.
     * botToken vazio = TelegramConfig cai no fallback de log (feature 017, mesmo padrao de SMTP).
     * baseUrl override-avel em teste (WireMock), mesmo padrao de Openmeteo. */
    public record Telegram(
            String botToken, String botUsername, String baseUrl, String webhookSecret, boolean habilitado) {}

    /** baseUrl override-avel em teste (WireMock) -- em producao aponta pra API real do Open-Meteo. */
    /** proxyKey vazio = nao envia X-Proxy-Key; o Worker da Cloudflare so cobra a chave quando a tem configurada. */
    public record Openmeteo(String baseUrl, String proxyKey) {}
}
