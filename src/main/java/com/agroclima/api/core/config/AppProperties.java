package com.agroclima.api.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Espelha app/core/config.py (pydantic-settings). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, String frontendBaseUrl, Smtp smtp) {

    public record Jwt(String secret, String algorithm, int expirationHours) {}

    public record Cors(List<String> origins, String originRegex) {}

    public record Smtp(String host, int port, String user, String password, String from) {}
}
