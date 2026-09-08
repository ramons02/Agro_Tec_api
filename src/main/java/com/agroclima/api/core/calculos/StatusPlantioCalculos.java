package com.agroclima.api.core.calculos;

/**
 * RN004-RN006. Porte literal de status_plantio.py. AMARELO e o fallback conservador para
 * todas as faixas implicitas (90-95% e 60-90% sem chuva prevista) -- nunca cai pra VERMELHO nelas.
 */
public final class StatusPlantioCalculos {

    public static final double LIMIAR_VERMELHO_SECA_PCT = 0.30;
    public static final double LIMIAR_VERMELHO_ENCHARCAMENTO_PCT = 0.95;
    public static final double LIMIAR_VERDE_MIN_PCT = 0.60;
    public static final double LIMIAR_VERDE_MAX_PCT = 0.90;
    public static final double CHUVA_PREVISTA_MINIMA_VERDE_MM = 5.0;

    private StatusPlantioCalculos() {}

    public static StatusPlantio classificarStatus(double armazenamentoMm, double cadMm, double chuvaPrevistaMm) {
        double percentualCad = armazenamentoMm / cadMm;
        if (percentualCad < LIMIAR_VERMELHO_SECA_PCT || percentualCad > LIMIAR_VERMELHO_ENCHARCAMENTO_PCT) {
            return StatusPlantio.VERMELHO;
        }
        if (percentualCad >= LIMIAR_VERDE_MIN_PCT
                && percentualCad <= LIMIAR_VERDE_MAX_PCT
                && chuvaPrevistaMm >= CHUVA_PREVISTA_MINIMA_VERDE_MM) {
            return StatusPlantio.VERDE;
        }
        return StatusPlantio.AMARELO;
    }
}
