package com.agroclima.api.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Espelha app/core/config.py (pydantic-settings). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt, Cors cors, String frontendBaseUrl, Smtp smtp, Inmet inmet, Openmeteo openmeteo,
        Telegram telegram, Redis redis) {

    public record Jwt(String secret, String algorithm, int expirationHours) {}

    public record Cors(List<String> origins, String originRegex) {}

    public record Smtp(String host, int port, String user, String password, String from) {}

    /** botToken vazio = TelegramConfig cai no fallback de log (feature 017, mesmo padrao de SMTP).
     * baseUrl override-avel em teste (WireMock), mesmo padrao de Inmet/Openmeteo. */
    public record Telegram(String botToken, String botUsername, String baseUrl, String webhookSecret) {}

    /** baseUrl override-avel em teste (WireMock) -- em producao aponta pra API real do INMET.
     * enabled=false faz a ingestao pular as medicoes do INMET e ir direto ao Open-Meteo; o catalogo
     * de estacoes segue sendo lido. */
    public record Inmet(String baseUrl, boolean enabled) {}

    /** baseUrl override-avel em teste (WireMock) -- em producao aponta pra API real do Open-Meteo. */
    /** proxyKey vazio = nao envia X-Proxy-Key; o Worker da Cloudflare so cobra a chave quando a tem configurada. */
    public record Openmeteo(String baseUrl, String proxyKey) {}

    /** url vazia/ausente = CacheRedisService nunca tenta conectar (spec 018, RNF019). */
    public record Redis(String url) {}
}
