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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final Cache<String, List<PrevisaoDiaria>> cache10Dias =
            Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(TTL_CACHE).build();
    private static final String HEADER_PROXY_KEY = "X-Proxy-Key";
    // Condicao atual muda a cada 15 min no provedor; 10 min de cache evita bater nele a cada requisicao.
    private final Cache<String, CondicaoAtual> cacheAtual =
            Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(10)).build();
    // Dia ja ocorrido nao muda: cache longo.
    private final Cache<String, DadosDiarios> cacheDiarios =
            Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofHours(6)).build();
    private final AtomicLong contadorChamadasReais = new AtomicLong();

    public OpenMeteoClient(AppProperties appProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(appProperties.openmeteo().baseUrl())
                .requestFactory(factory);
        String proxyKey = appProperties.openmeteo().proxyKey();
        if (proxyKey != null && !proxyKey.isBlank()) {
            builder.defaultHeader(HEADER_PROXY_KEY, proxyKey);
        }
        this.restClient = builder.build();
    }

    /** Para testes: os caches sao de singleton e atravessam testes que compartilham o contexto Spring. */
    public void limparCaches() {
        cache.invalidateAll();
        cache10Dias.invalidateAll();
        cacheAtual.invalidateAll();
        cacheDiarios.invalidateAll();
    }

    public long contadorChamadasReais() {
        return contadorChamadasReais.get();
    }

    /**
     * Condicao atual no ponto (campo "current" do provedor, atualizado a cada 15 min). Diferente de
     * obterPrevisao, cujos valores horarios comecam em 00:00 UTC do dia e nao representam "agora".
     */
    public CondicaoAtual obterCondicaoAtual(double lat, double lon) {
        String chave = chaveSemHora(lat, lon);
        CondicaoAtual emCache = cacheAtual.getIfPresent(chave);
        if (emCache != null) {
            return emCache;
        }
        JsonNode resposta = chamarComRetryEm429(() -> executarChamadaAtual(lat, lon));
        contadorChamadasReais.incrementAndGet();
        CondicaoAtual condicao = parsearAtual(resposta);
        cacheAtual.put(chave, condicao);
        return condicao;
    }

    /** Chuva e ET0 de um dia ja ocorrido (ate 3 dias atras), pelo campo "daily" com past_days. */
    public DadosDiarios obterDadosDiarios(double lat, double lon, LocalDate dia) {
        String chave = chaveSemHora(lat, lon) + "|" + dia;
        DadosDiarios emCache = cacheDiarios.getIfPresent(chave);
        if (emCache != null) {
            return emCache;
        }
        JsonNode resposta = chamarComRetryEm429(() -> executarChamadaDiaria(lat, lon));
        contadorChamadasReais.incrementAndGet();
        DadosDiarios dados = parsearDiarios(resposta, dia);
        cacheDiarios.put(chave, dados);
        return dados;
    }

    public PrevisaoClimatica obterPrevisao(double lat, double lon) {
        String chave = chaveCache(lat, lon);
        PrevisaoClimatica emCache = cache.getIfPresent(chave);
        if (emCache != null) {
            return emCache;
        }
        JsonNode resposta = chamarComRetryEm429(() -> executarChamada(lat, lon));
        contadorChamadasReais.incrementAndGet();
        PrevisaoClimatica previsao = parsear(resposta, lat, lon);
        cache.put(chave, previsao);
        return previsao;
    }

    /** Previsao diaria pros proximos 10 dias -- busca de cidade (nao usada no clima atual
     * nem no Balanco Hidrico, que usam obterCondicaoAtual/obterDadosDiarios/obterPrevisao). */
    public List<PrevisaoDiaria> obterPrevisao10Dias(double lat, double lon) {
        String chave = chaveCache(lat, lon);
        List<PrevisaoDiaria> emCache = cache10Dias.getIfPresent(chave);
        if (emCache != null) {
            return emCache;
        }
        JsonNode resposta = chamarComRetryEm429(() -> executarChamada10Dias(lat, lon));
        contadorChamadasReais.incrementAndGet();
        List<PrevisaoDiaria> previsao = parsear10Dias(resposta);
        cache10Dias.put(chave, previsao);
        return previsao;
    }

    private String chaveSemHora(double lat, double lon) {
        return Math.round(lat * 100) / 100.0 + "|" + Math.round(lon * 100) / 100.0;
    }

    private JsonNode executarChamadaAtual(double lat, double lon) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v1/forecast")
                        .queryParam("latitude", lat)
                        .queryParam("longitude", lon)
                        .queryParam("current", "temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m,wind_gusts_10m")
                        .queryParam("timezone", "UTC")
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode executarChamadaDiaria(double lat, double lon) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v1/forecast")
                        .queryParam("latitude", lat)
                        .queryParam("longitude", lon)
                        .queryParam("daily", "precipitation_sum,et0_fao_evapotranspiration")
                        .queryParam("past_days", 3)
                        .queryParam("forecast_days", 1)
                        .queryParam("timezone", "UTC")
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private CondicaoAtual parsearAtual(JsonNode resposta) {
        JsonNode atual = resposta == null ? null : resposta.path("current");
        if (atual == null || !atual.hasNonNull("temperature_2m") || !atual.hasNonNull("relative_humidity_2m")
                || !atual.hasNonNull("wind_speed_10m")) {
            throw new FontePrevisaoIndisponivelException("Open-Meteo devolveu resposta sem a condição atual.", null);
        }
        Instant medidoEm = Instant.now();
        if (atual.hasNonNull("time")) {
            try {
                medidoEm = LocalDateTime.parse(atual.get("time").asText()).toInstant(ZoneOffset.UTC);
            } catch (java.time.format.DateTimeParseException ignorada) {
                // mantem "agora": o horario e so informativo
            }
        }
        return new CondicaoAtual(
                atual.get("temperature_2m").asDouble(),
                atual.get("relative_humidity_2m").asDouble(),
                atual.get("wind_speed_10m").asDouble(),
                atual.hasNonNull("wind_gusts_10m") ? atual.get("wind_gusts_10m").asDouble() : null,
                atual.hasNonNull("precipitation") ? atual.get("precipitation").asDouble() : null,
                medidoEm);
    }

    private DadosDiarios parsearDiarios(JsonNode resposta, LocalDate dia) {
        JsonNode daily = resposta == null ? null : resposta.path("daily");
        JsonNode datas = daily == null ? null : daily.path("time");
        if (datas == null || !datas.isArray()) {
            throw new FontePrevisaoIndisponivelException("Open-Meteo devolveu resposta sem dados diários.", null);
        }
        for (int i = 0; i < datas.size(); i++) {
            if (dia.toString().equals(datas.get(i).asText())) {
                return new DadosDiarios(
                        numeroOuNulo(daily, "precipitation_sum", i), numeroOuNulo(daily, "et0_fao_evapotranspiration", i));
            }
        }
        return new DadosDiarios(null, null);
    }

    private Double numeroOuNulo(JsonNode secao, String campo, int indice) {
        JsonNode array = secao.path(campo);
        if (!array.isArray() || indice >= array.size() || !array.get(indice).isNumber()) {
            return null;
        }
        return array.get(indice).asDouble();
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
    private JsonNode chamarComRetryEm429(java.util.function.Supplier<JsonNode> chamada) {
        for (int tentativa = 0; tentativa <= 1; tentativa++) {
            try {
                return chamada.get();
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
                        .queryParam("hourly", "wind_speed_10m,wind_gusts_10m,wind_speed_100m,temperature_2m,relative_humidity_2m,soil_moisture_0_to_7cm,soil_moisture_7_to_28cm")
                        .queryParam("daily", "et0_fao_evapotranspiration,precipitation_sum")
                        .queryParam("timezone", "UTC")
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode executarChamada10Dias(double lat, double lon) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v1/forecast")
                        .queryParam("latitude", lat)
                        .queryParam("longitude", lon)
                        .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum,"
                                + "precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max")
                        .queryParam("forecast_days", 10)
                        .queryParam("timezone", "UTC")
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private List<PrevisaoDiaria> parsear10Dias(JsonNode resposta) {
        JsonNode daily = resposta.path("daily");
        JsonNode datas = daily.path("time");
        List<PrevisaoDiaria> dias = new ArrayList<>();
        for (int i = 0; i < datas.size(); i++) {
            dias.add(new PrevisaoDiaria(
                    LocalDate.parse(datas.get(i).asText()),
                    valorNoIndice(daily, "temperature_2m_min", i),
                    valorNoIndice(daily, "temperature_2m_max", i),
                    valorNoIndice(daily, "precipitation_sum", i),
                    valorNoIndice(daily, "precipitation_probability_max", i),
                    valorNoIndice(daily, "wind_speed_10m_max", i),
                    valorNoIndice(daily, "wind_gusts_10m_max", i)));
        }
        return dias;
    }

    private double valorNoIndice(JsonNode secao, String campo, int indice) {
        JsonNode array = secao.path(campo);
        if (!array.isArray() || indice >= array.size()) {
            return 0.0;
        }
        JsonNode valor = array.get(indice);
        return valor.isNumber() ? valor.asDouble() : 0.0;
    }

    private PrevisaoClimatica parsear(JsonNode resposta, double lat, double lon) {
        JsonNode hourly = resposta.path("hourly");
        JsonNode daily = resposta.path("daily");

        double vento10m = primeiroValor(hourly, "wind_speed_10m");
        double rajada10m = primeiroValor(hourly, "wind_gusts_10m");
        double vento100m = primeiroValor(hourly, "wind_speed_100m");
        Double temperatura2m = primeiroValorOuNull(hourly, "temperature_2m");
        Double umidadeAr2m = primeiroValorOuNull(hourly, "relative_humidity_2m");
        double umidadeSolo07 = primeiroValor(hourly, "soil_moisture_0_to_7cm");
        double et0 = primeiroValor(daily, "et0_fao_evapotranspiration");
        double precipitacaoPrevista = primeiroValor(daily, "precipitation_sum");

        Map<String, Double> outrasCamadas = new LinkedHashMap<>();
        outrasCamadas.put("soil_moisture_7_to_28cm", primeiroValor(hourly, "soil_moisture_7_to_28cm"));

        return new PrevisaoClimatica(
                lat, lon, vento10m, vento100m, rajada10m, temperatura2m, umidadeAr2m, et0, umidadeSolo07,
                outrasCamadas, precipitacaoPrevista, Instant.now());
    }

    private double primeiroValor(JsonNode secao, String campo) {
        JsonNode array = secao.path(campo);
        if (!array.isArray() || array.isEmpty()) {
            return 0.0;
        }
        JsonNode primeiro = array.get(0);
        return primeiro.isNumber() ? primeiro.asDouble() : 0.0;
    }

    /** Usado pra campos onde 0.0 tem significado real (ex: 0°C) -- ausencia vira null,
     * nunca um zero fabricado que passaria despercebido numa checagem de seguranca. */
    private Double primeiroValorOuNull(JsonNode secao, String campo) {
        JsonNode array = secao.path(campo);
        if (!array.isArray() || array.isEmpty()) {
            return null;
        }
        JsonNode primeiro = array.get(0);
        return primeiro.isNumber() ? primeiro.asDouble() : null;
    }
}
