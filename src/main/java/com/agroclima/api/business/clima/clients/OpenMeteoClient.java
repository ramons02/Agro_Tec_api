package com.agroclima.api.business.clima.clients;

import com.agroclima.api.core.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Espelha app/services/openmeteo_service.py (feature 003). Timeout 3s. Cache local
 * (Caffeine, TTL 30min, chave lat/lon arredondados + hora UTC) pra respeitar o limite
 * gratuito de 10k chamadas/dia (RNF012).
 */
@Component
public class OpenMeteoClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final Duration TTL_CACHE = Duration.ofMinutes(30);

    private final RestClient restClient;
    private final Cache<String, PrevisaoClimatica> cache =
            Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(TTL_CACHE).build();
    private final AtomicLong contadorChamadasReais = new AtomicLong();

    public OpenMeteoClient(AppProperties appProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(appProperties.openmeteo().baseUrl())
                .requestFactory(factory)
                .build();
    }

    public long contadorChamadasReais() {
        return contadorChamadasReais.get();
    }

    public PrevisaoClimatica obterPrevisao(double lat, double lon) {
        String chave = chaveCache(lat, lon);
        PrevisaoClimatica emCache = cache.getIfPresent(chave);
        if (emCache != null) {
            return emCache;
        }
        JsonNode resposta = chamarComRetryEm429(lat, lon);
        contadorChamadasReais.incrementAndGet();
        PrevisaoClimatica previsao = parsear(resposta, lat, lon);
        cache.put(chave, previsao);
        return previsao;
    }

    private String chaveCache(double lat, double lon) {
        double latArredondada = Math.round(lat * 100) / 100.0;
        double lonArredondada = Math.round(lon * 100) / 100.0;
        String hora = Instant.now().truncatedTo(ChronoUnit.HOURS).toString();
        return latArredondada + "|" + lonArredondada + "|" + hora;
    }

    /**
     * So reage a 429: uma unica espera de 5s e uma unica nova tentativa, so na primeira
     * tentativa -- proposital, nao generalizar pra retry-with-backoff. Um 429 aqui
     * normalmente reflete rate-limit compartilhado entre varias estacoes do mesmo ciclo
     * de ingestao, nao uma falha isolada (commit Python `375055f`).
     */
    private JsonNode chamarComRetryEm429(double lat, double lon) {
        for (int tentativa = 0; tentativa <= 1; tentativa++) {
            try {
                return executarChamada(lat, lon);
            } catch (HttpClientErrorException.TooManyRequests ex) {
                if (tentativa == 1) {
                    throw new FontePrevisaoIndisponivelException("Open-Meteo indisponível (429 persistente).", ex);
                }
                dormir5Segundos();
            } catch (RestClientException ex) {
                throw new FontePrevisaoIndisponivelException("Open-Meteo indisponível.", ex);
            }
        }
        throw new IllegalStateException("inalcançável");
    }

    private void dormir5Segundos() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException interrompida) {
            Thread.currentThread().interrupt();
            throw new FontePrevisaoIndisponivelException("Open-Meteo indisponível.", interrompida);
        }
    }

    private JsonNode executarChamada(double lat, double lon) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v1/forecast")
                        .queryParam("latitude", lat)
                        .queryParam("longitude", lon)
                        .queryParam("hourly", "wind_speed_10m,wind_speed_100m,soil_moisture_0_to_7cm,soil_moisture_7_to_28cm")
                        .queryParam("daily", "et0_fao_evapotranspiration,precipitation_sum")
                        .queryParam("timezone", "UTC")
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private PrevisaoClimatica parsear(JsonNode resposta, double lat, double lon) {
        JsonNode hourly = resposta.path("hourly");
        JsonNode daily = resposta.path("daily");

        double vento10m = primeiroValor(hourly, "wind_speed_10m");
        double vento100m = primeiroValor(hourly, "wind_speed_100m");
        double umidadeSolo07 = primeiroValor(hourly, "soil_moisture_0_to_7cm");
        double et0 = primeiroValor(daily, "et0_fao_evapotranspiration");
        double precipitacaoPrevista = primeiroValor(daily, "precipitation_sum");

        Map<String, Double> outrasCamadas = new LinkedHashMap<>();
        outrasCamadas.put("soil_moisture_7_to_28cm", primeiroValor(hourly, "soil_moisture_7_to_28cm"));

        return new PrevisaoClimatica(
                lat, lon, vento10m, vento100m, et0, umidadeSolo07, outrasCamadas, precipitacaoPrevista, Instant.now());
    }

    private double primeiroValor(JsonNode secao, String campo) {
        JsonNode array = secao.path(campo);
        if (!array.isArray() || array.isEmpty()) {
            return 0.0;
        }
        JsonNode primeiro = array.get(0);
        return primeiro.isNumber() ? primeiro.asDouble() : 0.0;
    }
}
