package com.agroclima.api.business.telegram;

import com.agroclima.api.core.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fabrica TelegramClient -- mesmo padrao de EmailConfig (feature 013). */
@Configuration
public class TelegramConfig {

    private final AppProperties appProperties;

    public TelegramConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public TelegramClient telegramClient() {
        AppProperties.Telegram telegram = appProperties.telegram();
        if (telegram == null || naoEstaConfigurado(telegram.botToken())) {
            return new LogTelegramClient();
        }
        return new HttpTelegramClient(telegram.baseUrl(), telegram.botToken());
    }

    private static boolean naoEstaConfigurado(String valor) {
        return valor == null || valor.isBlank();
    }
}
