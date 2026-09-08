package com.agroclima.api.core.calculos;

/**
 * RN001-RN003 (vento) e RN021/RN022 (Delta T, Escopo V3). Porte literal de pulverizacao.py.
 * Ordem de checagem e load-bearing: vento forte prevalece sobre inversao termica no overlap.
 */
public final class PulverizacaoCalculos {

    public static final double FATOR_MS_PARA_KMH = 3.6;
    public static final double LIMITE_VENTO_MIN_FAVORAVEL = 3.0;
    public static final double LIMITE_VENTO_MAX_FAVORAVEL = 10.0;
    public static final double LIMITE_RAJADA_MAX_FAVORAVEL = 15.0;
    public static final double LIMITE_DELTA_T_MIN_FAVORAVEL = 2.0;
    public static final double LIMITE_DELTA_T_MAX_FAVORAVEL = 10.0;

    private PulverizacaoCalculos() {}

    public static double converterMsParaKmh(double velocidadeMs) {
        return velocidadeMs * FATOR_MS_PARA_KMH;
    }

    public static ClassificacaoPulverizacao classificarPulverizacao(double ventoKmh, double rajadaKmh) {
        if (ventoKmh > LIMITE_VENTO_MAX_FAVORAVEL || rajadaKmh > LIMITE_RAJADA_MAX_FAVORAVEL) {
            return ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE;
        }
        if (ventoKmh < LIMITE_VENTO_MIN_FAVORAVEL) {
            return ClassificacaoPulverizacao.BLOQUEIO_INVERSAO_TERMICA;
        }
        return ClassificacaoPulverizacao.FAVORAVEL;
    }

    /** Checagem complementar a classificarPulverizacao -- nao substitui, so soma motivos de bloqueio. */
    public static ClassificacaoPulverizacao classificarDeltaT(double deltaTC) {
        if (deltaTC > LIMITE_DELTA_T_MAX_FAVORAVEL) {
            return ClassificacaoPulverizacao.BLOQUEIO_EVAPORACAO_EXCESSIVA;
        }
        if (deltaTC < LIMITE_DELTA_T_MIN_FAVORAVEL) {
            return ClassificacaoPulverizacao.BLOQUEIO_INVERSAO_TERMICA;
        }
        return ClassificacaoPulverizacao.FAVORAVEL;
    }
}
