package com.agroclima.api.business.clima.clients;

/** Espelha FonteIndisponivelError de app/services/inmet_service.py -- aciona fallback (RN009). */
public class FonteIndisponivelException extends RuntimeException {

    public FonteIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
