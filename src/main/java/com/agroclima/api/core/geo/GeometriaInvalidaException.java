package com.agroclima.api.core.geo;

/** Espelha ArquivoGeoInvalidoError de app/services/importacao_geo_service.py. */
public class GeometriaInvalidaException extends RuntimeException {

    public GeometriaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
