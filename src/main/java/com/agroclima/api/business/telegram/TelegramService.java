package com.agroclima.api.business.telegram;

import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.core.config.AppProperties;
import com.agroclima.api.core.response.AppException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Espelha o fluxo de token opaco de AuthService (feature 013), adaptado pro deep link do
 * Telegram em vez de link de email (research.md: "Fluxo de vinculo conta-chat"). */
@Service
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);
    private static final String PREFIXO_START = "/start ";

    private final UsuarioRepository usuarioRepository;
    private final TokenVinculoTelegramRepository tokenRepository;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public TelegramService(
            UsuarioRepository usuarioRepository,
            TokenVinculoTelegramRepository tokenRepository,
            AppProperties appProperties) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.appProperties = appProperties;
    }

    @Transactional
    public String gerarLinkVinculo(UUID usuarioId) {
        String token = gerarTokenOpaco();
        tokenRepository.save(new TokenVinculoTelegram(usuarioId, token));
        String botUsername = appProperties.telegram().botUsername();
        if (botUsername == null || botUsername.isBlank()) {
            throw new AppException(503, "Integração com Telegram não configurada nesta instância.");
        }
        return "https://t.me/" + botUsername + "?start=" + token;
    }

    /** Nunca lanca excecao -- token invalido/expirado e so ignorado/logado (contracts/telegram.md:
     * "a Telegram nao trata corpo de erro de forma especial"), sempre responde {"ok": true}. */
    @Transactional
    public void processarWebhook(JsonNode payload) {
        JsonNode mensagem = payload.path("message");
        String texto = mensagem.path("text").asText("");
        if (!texto.startsWith(PREFIXO_START)) {
            return;
        }
        String token = texto.substring(PREFIXO_START.length()).trim();
        String chatId = mensagem.path("chat").path("id").asText(null);
        if (token.isBlank() || chatId == null) {
            return;
        }

        Instant agora = Instant.now();
        TokenVinculoTelegram tokenEntity = tokenRepository.findByToken(token).orElse(null);
        if (tokenEntity == null || !tokenEntity.estaValido(agora)) {
            log.warn("Webhook Telegram com token inválido ou expirado (chat {}).", chatId);
            return;
        }

        Usuario usuario = usuarioRepository.findById(tokenEntity.getUsuarioId()).orElse(null);
        if (usuario == null) {
            log.warn("Webhook Telegram: usuário do token {} não existe mais.", tokenEntity.getId());
            return;
        }

        usuario.vincularTelegram(chatId);
        tokenEntity.marcarUsado(agora);
        usuarioRepository.save(usuario);
        tokenRepository.save(tokenEntity);
    }

    @Transactional
    public void desvincular(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new AppException(404, "Usuário não encontrado."));
        usuario.desvincularTelegram();
        usuarioRepository.save(usuario);
    }

    private String gerarTokenOpaco() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
