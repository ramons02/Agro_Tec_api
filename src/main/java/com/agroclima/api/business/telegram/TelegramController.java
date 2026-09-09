package com.agroclima.api.business.telegram;

import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.security.UsuarioAutenticado;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Espelha contracts/telegram.md (feature 017). O webhook e chamado pela propria Telegram Bot
 * API, nunca pelo frontend -- liberado em SecurityConfig sem exigir Bearer token. */
@RestController
@RequestMapping("/api/v1/telegram")
public class TelegramController {

    private final TelegramService telegramService;

    public TelegramController(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    @PostMapping("/vincular-link")
    public ApiEnvelope<Map<String, String>> vincularLink(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        String link = telegramService.gerarLinkVinculo(usuario.id());
        return ApiEnvelope.sucesso(Map.of("link", link));
    }

    @PostMapping("/webhook")
    public Map<String, Boolean> webhook(@RequestBody JsonNode payload) {
        telegramService.processarWebhook(payload);
        return Map.of("ok", true);
    }

    @DeleteMapping("/vinculo")
    public ResponseEntity<Void> desvincular(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        telegramService.desvincular(usuario.id());
        return ResponseEntity.noContent().build();
    }
}
