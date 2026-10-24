package com.agroclima.api.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Relogio injetavel: producao usa UTC do sistema; os testes substituem para avancar o tempo (expiracao de 7 dias). */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
