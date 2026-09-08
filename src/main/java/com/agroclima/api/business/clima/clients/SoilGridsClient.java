package com.agroclima.api.business.clima.clients;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Optional;

/**
 * Espelha app/services/soilgrids_service.py (feature 004). Timeout 5s (mais frouxo que
 * INMET/Open-Meteo -- e uma consulta pontual na criacao do talhao, nao hot-path).
 * "Sem cobertura" (layers vazio ou clay/sand/silt ausentes) e Optional.empty() -- resposta
 * legitima, diferente de FonteSoloIndisponivelException (erro de rede/HTTP).
 */
@Component
public class SoilGridsClient {

    private static final String URL = "https://rest.isric.org/soilgrids/v2.0/properties/query";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String PROFUNDIDADE = "0-5cm";

    private final RestClient restClient;

    public SoilGridsClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());
        this.restClient = RestClient.builder().baseUrl(URL).requestFactory(factory).build();
    }

    public Optional<PerfilSolo> parametrizarSolo(double lat, double lon) {
        JsonNode resposta;
        try {
            resposta = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("lon", lon)
                            .queryParam("lat", lat)
                            .queryParam("property", "clay", "sand", "silt", "soc", "bdod")
                            .queryParam("depth", PROFUNDIDADE)
                            .queryParam("value", "mean")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new FonteSoloIndisponivelException("SoilGrids indisponível.", ex);
        }

        JsonNode layers = resposta.path("properties").path("layers");
        if (!layers.isArray() || layers.isEmpty()) {
            return Optional.empty();
        }

        Double argilaGKg = valorCamadaBruto(layers, "clay");
        Double areiaGKg = valorCamadaBruto(layers, "sand");
        Double silteGKg = valorCamadaBruto(layers, "silt");
        if (argilaGKg == null || areiaGKg == null || silteGKg == null) {
            return Optional.empty();
        }
        Double socGKg = valorCamadaBruto(layers, "soc");
        Double bdodCgCm3 = valorCamadaBruto(layers, "bdod");

        double materiaOrganicaPct = (socGKg != null ? socGKg : 0.0) / 10.0;
        double densidadeSoloGCm3 = (bdodCgCm3 != null ? bdodCgCm3 : 130.0) / 100.0;

        return Optional.of(new PerfilSolo(
                argilaGKg / 10.0, areiaGKg / 10.0, silteGKg / 10.0, materiaOrganicaPct, densidadeSoloGCm3));
    }

    /** Valor cru na unidade nativa do SoilGrids (g/kg pra textura/soc, cg/cm3 pra bdod) -- ja dividido pelo d_factor. */
    private Double valorCamadaBruto(JsonNode layers, String nomePropriedade) {
        for (JsonNode layer : layers) {
            if (!nomePropriedade.equals(layer.path("name").asText())) {
                continue;
            }
            double dFactor = layer.path("unit_measure").path("d_factor").asDouble(1.0);
            if (dFactor == 0.0) {
                dFactor = 1.0;
            }
            for (JsonNode depth : layer.path("depths")) {
                if (PROFUNDIDADE.equals(depth.path("label").asText())) {
                    JsonNode media = depth.path("values").path("mean");
                    if (media.isMissingNode() || media.isNull()) {
                        return null;
                    }
                    return media.asDouble() / dFactor;
                }
            }
        }
        return null;
    }
}
