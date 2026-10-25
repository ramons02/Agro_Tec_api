package com.agroclima.api.business.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Envio por API HTTPS do Brevo (POST /v3/smtp/email). Existe porque o Render gratis bloqueia as portas SMTP
 * (25, 465 e 587) desde 26/09/2025; HTTPS na 443 passa. Como o Telegram, falha de envio so loga: quem pede a
 * redefinicao nao pode descobrir, pela resposta, se o e-mail existe ou se o provedor caiu.
 */
public class BrevoEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final String apiKey;
    private final String remetente;
    private final String nomeRemetente;

    public BrevoEmailService(String baseUrl, String apiKey, String remetente, String nomeRemetente) {
        this.apiKey = apiKey;
        this.remetente = remetente;
        this.nomeRemetente = nomeRemetente;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public void enviarRecuperacaoSenha(String destinatario, String link) {
        Map<String, Object> corpo = Map.of(
                "sender", Map.of("email", remetente, "name", nomeRemetente),
                "to", List.of(Map.of("email", destinatario)),
                "subject", "Redefinição de senha, AgroClima Pará",
                "textContent", "Clique no link abaixo para redefinir sua senha (válido por 1 hora):\n\n" + link);
        try {
            restClient.post()
                    .uri("/v3/smtp/email")
                    .header("api-key", apiKey)
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            // A mensagem da excecao nao carrega a chave (ela vai em cabecalho), mas tambem nao o e-mail do usuario.
            log.error("Falha ao enviar o e-mail de redefinição de senha pelo Brevo: {}", ex.getMessage());
        }
    }
}
