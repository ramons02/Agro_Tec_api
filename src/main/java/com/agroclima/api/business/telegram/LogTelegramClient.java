package com.agroclima.api.business.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fallback quando TELEGRAM_BOT_TOKEN nao esta configurado -- nunca finge sucesso, so loga
 * em WARN (mesmo padrao de LogEmailService, feature 013). */
public class LogTelegramClient implements TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(LogTelegramClient.class);

    @Override
    public void enviarMensagem(String chatId, String texto) {
        log.warn("TELEGRAM_BOT_TOKEN não configurado — mensagem não enviada ao chat {}: {}", chatId, texto);
    }
}
