package com.agroclima.api.core.response;

/** Envelope de erro -- espelha envelope_erro() de app/core/response.py. */
public record ErrorEnvelope(String status, int codigo, String mensagem, Object detalhes) {

    public static ErrorEnvelope of(int codigo, String mensagem, Object detalhes) {
        return new ErrorEnvelope("erro", codigo, mensagem, detalhes);
    }
}
