package com.agroclima.api.business.clima.clients;

/** Espelha FontePrevisaoIndisponivelError de app/services/openmeteo_service.py. */
public class FontePrevisaoIndisponivelException extends RuntimeException {

    public FontePrevisaoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
