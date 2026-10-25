package com.agroclima.api.business.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manda a notificação para todos os aparelhos da pessoa. Falha de envio nunca interrompe quem chamou (mesmo padrão
 * de degradação do Telegram): o alerta já está gravado no sino. Aparelho que o serviço de push diz que não existe
 * mais (404/410) perde a assinatura.
 */
@Service
public class NotificadorPush {

    private static final Logger log = LoggerFactory.getLogger(NotificadorPush.class);
    private static final int TTL_SEGUNDOS = 3 * 60 * 60; // alerta de vento velho de 3 h nao serve mais
    private static final int TIMEOUT_SEGUNDOS = 10;

    private final AssinaturaPushRepository repositorio;
    private final ChavesVapid chaves;
    private final ObjectMapper json = new ObjectMapper();
    private final String assunto;

    public NotificadorPush(
            AssinaturaPushRepository repositorio,
            ChavesVapid chaves,
            @Value("${app.push.subject:}") String assuntoConfigurado,
            @Value("${app.frontend-base-url:}") String frontendBaseUrl) {
        this.repositorio = repositorio;
        this.chaves = chaves;
        // O assunto VAPID e um contato (mailto: ou https:) que o servico de push pode usar em caso de abuso.
        this.assunto = !assuntoConfigurado.isBlank() ? assuntoConfigurado
                : frontendBaseUrl.startsWith("https://") ? frontendBaseUrl : "mailto:contato@agroclima.invalid";
    }

    /** Devolve em quantos aparelhos o serviço de push aceitou a mensagem (0 = nenhum aparelho ou todos falharam). */
    public int enviar(UUID usuarioId, String titulo, String mensagem, String url) {
        var assinaturas = repositorio.findByUsuarioId(usuarioId);
        if (assinaturas.isEmpty()) {
            return 0;
        }
        int aceitos = 0;
        String corpo;
        try {
            corpo = json.writeValueAsString(Map.of("titulo", titulo, "mensagem", mensagem, "url", url));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.error("Falha ao montar a notificação push: {}", ex.getMessage());
            return 0;
        }
        ChavesVapid.Par par = chaves.obter();
        for (AssinaturaPush assinatura : assinaturas) {
            try {
                PushService servico = new PushService(par.publica(), par.privada(), assunto);
                var resposta = servico.sendAsync(new Notification(
                        assinatura.getEndpoint(), assinatura.getP256dh(), assinatura.getAuth(), corpo.getBytes(java.nio.charset.StandardCharsets.UTF_8), TTL_SEGUNDOS))
                        .get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
                int status = resposta.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    repositorio.apagarPorEndpoint(assinatura.getEndpoint());
                } else if (status >= 300) {
                    log.warn("Serviço de push respondeu {} para uma assinatura da conta {}.", status, usuarioId);
                } else {
                    aceitos++;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return aceitos;
            } catch (Exception ex) {
                log.warn("Falha ao enviar push para uma assinatura da conta {}: {}", usuarioId, ex.getMessage());
            }
        }
        return aceitos;
    }
}
