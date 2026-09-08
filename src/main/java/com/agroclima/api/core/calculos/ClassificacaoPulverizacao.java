package com.agroclima.api.core.calculos;

/** RD007 -- 3 estados de vento + 1 estado adicional de Delta T (Escopo V3). */
public enum ClassificacaoPulverizacao {
    FAVORAVEL,
    BLOQUEIO_VENTO_FORTE,
    BLOQUEIO_INVERSAO_TERMICA,
    BLOQUEIO_EVAPORACAO_EXCESSIVA
}
