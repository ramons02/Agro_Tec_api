package com.agroclima.api.business.clima.clients;

import java.time.Instant;

public record MedicaoInmetDto(
        Double precipitacaoMm,
        Double temperaturaC,
        Double umidadePct,
        Double ventoVelocidadeMs,
        Double ventoRajadaMs,
        Instant dataHoraUtc) {}
