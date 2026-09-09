package com.agroclima.api.business.clima.clients;

import java.time.LocalDate;

/** Um dia da previsao de 10 dias da Open-Meteo (feature nova: busca por cidade). */
public record PrevisaoDiaria(
        LocalDate data,
        double temperaturaMinC,
        double temperaturaMaxC,
        double precipitacaoPrevistaMm,
        double probabilidadeChuvaPct,
        double ventoMaxKmh,
        double rajadaMaxKmh) {}
