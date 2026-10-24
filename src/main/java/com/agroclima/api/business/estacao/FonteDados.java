package com.agroclima.api.business.estacao;

/**
 * RF033 -- procedencia da leitura: medicao real de estacao do INMET no momento (AO_VIVO), ultima
 * leitura valida ja desatualizada (CACHE_EXPIRADO) ou valor de modelo do Open-Meteo usado como
 * fallback quando o INMET nao responde (PREVISAO) -- nunca e medicao de estacao.
 */
public enum FonteDados {
    AO_VIVO,
    CACHE_EXPIRADO,
    PREVISAO
}
