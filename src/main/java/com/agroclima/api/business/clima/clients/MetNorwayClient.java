package com.agroclima.api.business.clima.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;

/**
 * Segundo provedor de condicao atual (reserva do Open-Meteo): API Locationforecast do MET Norway,
 * gratuita, sem chave, mas exige User-Agent identificando o app com contato e atribuicao.
 * E valor de modelo, nunca medicao de estacao. Nos tropicos nao traz rajada de vento, entao
 * so vento medio; ET0 e umidade do solo tambem nao existem aqui (o balanco hidrico segue no
 * Open-Meteo). Cache de 30 min por coordenada arredondada, bem abaixo do limite deles.
 */
@Component
public class MetNorwayClient {

    static final String USER_AGENT_PADRAO = "AgroClimaPara/1.0 (https://github.com/ramons02/Agro_Tec_api)";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TTL_CACHE = Duration.ofMinutes(30);

    private final RestClient restClient;
    private final Cache<String, CondicaoAtual> cache =
            Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(TTL_CACHE).build();

    public MetNorwayClient(
            @Value("${app.metno.base-url:https://api.met.no}") String baseUrl,
            @Value("${app.metno.user-agent:}") String userAgent) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());
        String agente = (userAgent == null || userAgent.isBlank()) ? USER_AGENT_PADRAO : userAgent;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("User-Agent", agente)
                .build();
    }

    /** Para testes: o cache e de singleton e atravessa testes que compartilham o contexto Spring. */
    public void limparCache() {
        cache.invalidateAll();
    }

    public CondicaoAtual obterCondicaoAtual(double lat, double lon) {
        double latArredondada = Math.round(lat * 100) / 100.0;
        double lonArredondada = Math.round(lon * 100) / 100.0;
        String chave = latArredondada + "," + lonArredondada;
        CondicaoAtual emCache = cache.getIfPresent(chave);
        if (emCache != null) {
            return emCache;
        }
        CondicaoAtual condicao = buscar(latArredondada, lonArredondada);
        cache.put(chave, condicao);
        return condicao;
    }

    private CondicaoAtual buscar(double lat, double lon) {
        JsonNode resposta;
        try {
            resposta = restClient.get()
                    .uri(uri -> uri.path("/weatherapi/locationforecast/2.0/complete")
                            .queryParam("lat", lat)
                            .queryParam("lon", lon)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new FontePrevisaoIndisponivelException("MET Norway indisponível.", ex);
        }
        JsonNode primeira = resposta == null ? null : resposta.path("properties").path("timeseries").path(0);
        JsonNode detalhes = primeira == null ? null : primeira.path("data").path("instant").path("details");
        if (detalhes == null || !detalhes.hasNonNull("air_temperature") || !detalhes.hasNonNull("relative_humidity")
                || !detalhes.hasNonNull("wind_speed")) {
            throw new FontePrevisaoIndisponivelException("MET Norway devolveu resposta sem os campos esperados.", null);
        }
        JsonNode chuva = primeira.path("data").path("next_1_hours").path("details").path("precipitation_amount");
        return new CondicaoAtual(
                detalhes.get("air_temperature").asDouble(),
                detalhes.get("relative_humidity").asDouble(),
                detalhes.get("wind_speed").asDouble() * 3.6,
                null,
                chuva.isNumber() ? chuva.asDouble() : null,
                Instant.now());
    }
}
