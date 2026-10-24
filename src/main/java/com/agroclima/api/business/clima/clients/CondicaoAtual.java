package com.agroclima.api.business.clima.clients;

import java.time.Instant;

/**
 * Condicao meteorologica atual no ponto, vinda de um modelo (Open-Meteo ou MET Norway) -- nunca
 * medicao de estacao. rajadaKmh e precipitacaoMm sao nulos quando o provedor nao os fornece
 * (o MET Norway nao traz rajada nos tropicos).
 */
public record CondicaoAtual(
        double temperaturaC,
        double umidadePct,
        double ventoKmh,
        Double rajadaKmh,
        Double precipitacaoMm,
        Instant obtidoEmUtc) {}
