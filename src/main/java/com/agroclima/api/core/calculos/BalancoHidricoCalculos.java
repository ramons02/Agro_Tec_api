package com.agroclima.api.core.calculos;

/** RN007 -- Balanco Hidrico do Solo. Porte literal de app/core/calculos/balanco_hidrico.py. */
public final class BalancoHidricoCalculos {

    public static final double KC_FASE_INICIAL = 0.4;
    public static final double ARM_INICIAL_FRACAO_CAD = 0.70;

    private BalancoHidricoCalculos() {}

    public static double calcularArmazenamento(double armAnteriorMm, double precipitacaoMm, double et0Mm, double cadMm) {
        return calcularArmazenamento(armAnteriorMm, precipitacaoMm, et0Mm, cadMm, KC_FASE_INICIAL);
    }

    public static double calcularArmazenamento(
            double armAnteriorMm, double precipitacaoMm, double et0Mm, double cadMm, double kc) {
        double etReal = et0Mm * kc;
        double armBruto = armAnteriorMm + precipitacaoMm - etReal;
        return Math.min(cadMm, Math.max(0.0, armBruto));
    }

    public static double armazenamentoInicial(double cadMm) {
        return ARM_INICIAL_FRACAO_CAD * cadMm;
    }
}
