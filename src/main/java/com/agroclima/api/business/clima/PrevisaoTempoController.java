package com.agroclima.api.business.clima;

import com.agroclima.api.business.clima.clients.FontePrevisaoIndisponivelException;
import com.agroclima.api.business.clima.clients.OpenMeteoClient;
import com.agroclima.api.business.clima.clients.PrevisaoDiaria;
import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.response.AppException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Previsao de 10 dias por coordenada (busca de cidade no frontend, que ja tem a lista de
 * municipios do Para com lat/lon). Nao tem relacao com Talhao/Propriedade -- so consulta a
 * Open-Meteo direto, sem escopo de dono (qualquer usuario autenticado pode consultar
 * qualquer coordenada, igual ao /mapa/dados).
 */
@RestController
@RequestMapping("/api/v1/previsao")
public class PrevisaoTempoController {

    private final OpenMeteoClient openMeteoClient;

    public PrevisaoTempoController(OpenMeteoClient openMeteoClient) {
        this.openMeteoClient = openMeteoClient;
    }

    @GetMapping
    public ApiEnvelope<Map<String, Object>> previsao10Dias(
            @RequestParam double lat, @RequestParam double lon) {
        List<PrevisaoDiaria> dias;
        try {
            dias = openMeteoClient.obterPrevisao10Dias(lat, lon);
        } catch (FontePrevisaoIndisponivelException ex) {
            throw new AppException(503, "Previsão do tempo indisponível no momento. Tente novamente em instantes.");
        }

        List<Map<String, Object>> itens = dias.stream().map(dia -> {
            Map<String, Object> item = new HashMap<>();
            item.put("data", dia.data().toString());
            item.put("temperatura_min_c", dia.temperaturaMinC());
            item.put("temperatura_max_c", dia.temperaturaMaxC());
            item.put("precipitacao_prevista_mm", dia.precipitacaoPrevistaMm());
            item.put("probabilidade_chuva_pct", dia.probabilidadeChuvaPct());
            item.put("vento_max_kmh", dia.ventoMaxKmh());
            item.put("rajada_max_kmh", dia.rajadaMaxKmh());
            return item;
        }).toList();

        return ApiEnvelope.sucesso(Map.of("dias", itens));
    }
}
