package com.agroclima.api.business.telegram;

import com.agroclima.api.core.config.AppProperties;
import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.security.UsuarioAutenticado;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Espelha contracts/telegram.md (feature 017). O webhook e chamado pela propria Telegram Bot
 * API, nunca pelo frontend -- liberado em SecurityConfig sem exigir Bearer token. */
@RestController
@RequestMapping("/api/v1/telegram")
public class TelegramController {

    private static final String HEADER_SEGREDO_WEBHOOK = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramService telegramService;
    private final AppProperties appProperties;

    public TelegramController(TelegramService telegramService, AppProperties appProperties) {
        this.telegramService = telegramService;
        this.appProperties = appProperties;
    }

    @PostMapping("/vincular-link")
    public ApiEnvelope<Map<String, String>> vincularLink(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        String link = telegramService.gerarLinkVinculo(usuario.id());
        return ApiEnvelope.sucesso(Map.of("link", link));
    }

    @PostMapping("/webhook")
    public Map<String, Boolean> webhook(
            @RequestHeader(value = HEADER_SEGREDO_WEBHOOK, required = false) String segredoRecebido,
            @RequestBody JsonNode payload) {
        validarSegredoDoWebhook(segredoRecebido);
        telegramService.processarWebhook(payload);
        return Map.of("ok", true);
    }

    /** Falha fechada: sem TELEGRAM_WEBHOOK_SECRET configurado nenhum webhook e aceito. O mesmo valor
     * precisa ser passado como secret_token no setWebhook do bot; a Telegram o reenvia no header. */
    private void validarSegredoDoWebhook(String segredoRecebido) {
        String esperado = appProperties.telegram().webhookSecret();
        if (esperado == null || esperado.isBlank() || segredoRecebido == null
                || !MessageDigest.isEqual(
                        esperado.getBytes(StandardCharsets.UTF_8), segredoRecebido.getBytes(StandardCharsets.UTF_8))) {
            throw new AppException(403, "Webhook não autorizado.");
        }
    }

    @DeleteMapping("/vinculo")
    public ResponseEntity<Void> desvincular(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        telegramService.desvincular(usuario.id());
        return ResponseEntity.noContent().build();
    }
}
