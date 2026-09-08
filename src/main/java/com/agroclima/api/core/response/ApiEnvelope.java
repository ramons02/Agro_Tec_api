package com.agroclima.api.core.response;

import java.time.Instant;

/** Envelope de sucesso -- espelha envelope_sucesso() de app/core/response.py. */
public record ApiEnvelope<T>(String status, Instant dataConsultaUtc, T dados) {

    public static <T> ApiEnvelope<T> sucesso(T dados) {
        return new ApiEnvelope<>("sucesso", Instant.now(), dados);
    }
}
