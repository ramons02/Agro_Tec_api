package com.agroclima.api.core.base;

import java.util.UUID;

/** Projecao de consultas "quantos X por usuario" (GROUP BY), para evitar uma consulta por linha. */
public interface ContagemPorUsuario {

    UUID getUsuarioId();

    long getTotal();
}
