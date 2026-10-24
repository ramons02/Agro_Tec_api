package com.agroclima.api.business.clima;

/**
 * RF033 -- procedencia da leitura. Nao ha mais estacao de medicao: toda condicao atual vem de
 * modelo meteorologico (PREVISAO = Open-Meteo, ou MET Norway se ele falhar). CACHE_EXPIRADO e a
 * ultima leitura boa, ja desatualizada, usada quando nenhum provedor responde.
 */
public enum FonteDados {
    PREVISAO,
    CACHE_EXPIRADO
}
