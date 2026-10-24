package com.agroclima.api.business.clima.clients;

import java.time.LocalDate;

/**
 * Um dia da previsao de 10 dias (busca por cidade). Campo que a fonte nao entregou vem nulo,
 * nunca zero: zero de chuva ou de vento seria dado inventado (FR-009, RN008).
 */
public record PrevisaoDiaria(
        LocalDate data,
        Double temperaturaMinC,
        Double temperaturaMaxC,
        Double precipitacaoPrevistaMm,
        Double probabilidadeChuvaPct,
        Double ventoMaxKmh,
        Double rajadaMaxKmh) {}
