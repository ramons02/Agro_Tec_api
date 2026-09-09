package com.agroclima.api.business.telegram;

/** Espelha EmailService (feature 013) -- interface fina, duas implementacoes (real e
 * fallback de log), FR-004: nunca lanca excecao pro chamador em caso de falha de envio. */
public interface TelegramClient {

    void enviarMensagem(String chatId, String texto);
}
