package com.agroclima.api.business.clima;

import com.agroclima.api.business.clima.clients.FontePrevisaoIndisponivelException;
import com.agroclima.api.business.clima.clients.MetNorwayClient;
import com.agroclima.api.business.clima.clients.OpenMeteoClient;
import com.agroclima.api.business.clima.clients.PrevisaoDiaria;
import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.response.AppException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Previsao de 10 dias por coordenada (busca de cidade no frontend, que ja tem a lista de
 * municipios do Para com lat/lon). Nao tem relacao com Talhao/Propriedade -- qualquer usuario
 * autenticado pode consultar qualquer coordenada valida, igual ao /mapa/dados. Fonte principal:
 * Open-Meteo; se cair, reserva MET Norway, e a resposta diz qual das duas foi usada.
 */
@RestController
@RequestMapping("/api/v1/previsao")
public class PrevisaoTempoController {

    static final String FONTE_PRINCIPAL = "OPEN_METEO";
    static final String FONTE_RESERVA = "MET_NORWAY";

    private final OpenMeteoClient openMeteoClient;
    private final MetNorwayClient metNorwayClient;

    public PrevisaoTempoController(OpenMeteoClient openMeteoClient, MetNorwayClient metNorwayClient) {
        this.openMeteoClient = openMeteoClient;
        this.metNorwayClient = metNorwayClient;
    }

    @GetMapping
    public ApiEnvelope<Map<String, Object>> previsao10Dias(
            @RequestParam double lat, @RequestParam double lon) {
        // Escrito assim para recusar tambem NaN, que passa em qualquer comparacao "fora da faixa".
        if (!(lat >= -90 && lat <= 90)) {
            throw new AppException(422, "Latitude deve estar entre -90 e 90.");
        }
        if (!(lon >= -180 && lon <= 180)) {
            throw new AppException(422, "Longitude deve estar entre -180 e 180.");
        }

        String fonte = FONTE_PRINCIPAL;
        List<PrevisaoDiaria> dias;
        try {
            dias = openMeteoClient.obterPrevisao10Dias(lat, lon);
        } catch (FontePrevisaoIndisponivelException principal) {
            try {
                dias = metNorwayClient.obterPrevisao10Dias(lat, lon);
                fonte = FONTE_RESERVA;
            } catch (FontePrevisaoIndisponivelException reserva) {
                throw new AppException(503, "Previsão do tempo indisponível no momento. Tente novamente em instantes.");
            }
        }

        List<Map<String, Object>> itens = dias.stream().map(dia -> {
            // HashMap aceita valor nulo: campo que a fonte nao entregou vai vazio, nunca 0.
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

        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("fonte", fonte);
        dados.put("dias", itens);
        return ApiEnvelope.sucesso(dados);
    }
}
