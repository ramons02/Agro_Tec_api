package com.agroclima.api.business.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fallback quando SMTP nao esta configurado -- nunca finge sucesso, so loga o link em WARN. */
public class LogEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LogEmailService.class);

    @Override
    public void enviarRecuperacaoSenha(String destinatario, String link) {
        log.warn("SMTP não configurado — link de recuperação de senha para {}: {}", destinatario, link);
    }
}
