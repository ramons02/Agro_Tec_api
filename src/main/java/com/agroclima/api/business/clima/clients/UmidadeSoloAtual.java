package com.agroclima.api.business.clima.clients;

import java.time.Instant;

/**
 * Umidade volumetrica do solo (m3/m3) no ponto, estimada pelo modelo do Open-Meteo para a hora atual --
 * nunca medida por sensor. Os campos sao nulos quando o provedor nao traz a camada.
 */
public record UmidadeSoloAtual(Double camada0a7cm, Double camada7a28cm, Instant referenciaUtc) {}
