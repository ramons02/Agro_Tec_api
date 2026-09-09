package com.agroclima.api.business.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

/** Chamada HTTP direta a `sendMessage` da Telegram Bot API (research.md: JSON simples o
 * suficiente pra nao justificar dependencia dedicada). FR-004 -- falha de envio nunca
 * propaga excecao, so loga e segue (mesmo padrao de degradacao graciosa do INMET/SoilGrids). */
public class HttpTelegramClient implements TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(HttpTelegramClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final String botToken;

    public HttpTelegramClient(String baseUrl, String botToken) {
        this.botToken = botToken;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public void enviarMensagem(String chatId, String texto) {
        try {
            restClient.post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .body(Map.of("chat_id", chatId, "text", texto))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.error("Falha ao enviar mensagem via Telegram pro chat {}: {}", chatId, ex.getMessage());
        }
    }
}
