package com.agroclima.api.business.clima.clients;

/**
 * Chuva e evapotranspiracao de referencia (ET0) de um dia ja ocorrido, no ponto, segundo a analise do
 * modelo do Open-Meteo. Campo nulo = o provedor nao trouxe valor para o dia.
 */
public record DadosDiarios(Double precipitacaoMm, Double et0Mm) {}
