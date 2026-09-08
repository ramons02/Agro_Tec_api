package com.agroclima.api.business.vinculo;

/** Convite nunca vale sozinho, precisa de aceite (FR-005). REVOGADO existe no enum mas nenhum fluxo o usa ainda. */
public enum EstadoVinculo {
    CONVIDADO,
    ACEITO,
    REVOGADO
}
