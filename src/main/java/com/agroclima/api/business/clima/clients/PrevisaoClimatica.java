package com.agroclima.api.business.clima.clients;

import java.time.Instant;
import java.util.Map;

public record PrevisaoClimatica(
        double latitude,
        double longitude,
        double vento10mKmh,
        double vento100mKmh,
        double evapotranspiracaoMm,
        double umidadeSolo0a7cm,
        Map<String, Double> umidadeSoloOutrasCamadas,
        double precipitacaoPrevistaMm,
        Instant obtidoEmUtc) {}
