package com.agroclima.api.business.clima.clients;

/** Espelha FonteSoloIndisponivelError de app/services/soilgrids_service.py -- distinto de "sem cobertura" (Optional.empty()). */
public class FonteSoloIndisponivelException extends RuntimeException {

    public FonteSoloIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
