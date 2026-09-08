package com.agroclima.api.business.clima.clients;

import com.agroclima.api.core.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Espelha app/services/inmet_service.py (feature 002). Timeout 3s (RN009 -- aciona
 * fallback pro Open-Meteo se estourar). Sem retry aqui -- falha vai direto pro
 * IngestaoService decidir o fallback. Requer User-Agent de navegador (a API do INMET
 * bloqueia UA padrao de HTTP client).
 *
 * ATENCAO: os nomes de campo do catalogo /estacoes/T (CD_ESTACAO/DC_NOME/SG_ESTADO/
 * VL_LATITUDE/VL_LONGITUDE) sao a melhor suposicao com base na convencao publica do
 * INMET -- nao foram confirmados contra uma resposta real da API. Conferir contra o
 * payload real antes de rodar o seed em producao.
 */
@Component
public class InmetClient {

    private static final Logger log = LoggerFactory.getLogger(InmetClient.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm");

    private final RestClient restClient;

    public InmetClient(AppProperties appProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(appProperties.inmet().baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
    }

    public Optional<MedicaoInmetDto> buscarMedicaoRecente(String codigoEstacao) {
        JsonNode resposta;
        try {
            resposta = restClient.get()
                    .uri("/estacao/dados/{codigo}", codigoEstacao)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new FonteIndisponivelException("INMET indisponível para a estação " + codigoEstacao, ex);
        }

        if (resposta == null || !resposta.isArray() || resposta.isEmpty()) {
            return Optional.empty();
        }

        List<JsonNode> registros = new ArrayList<>();
        resposta.forEach(registros::add);
        registros.sort(Comparator.comparing(this::chaveOrdenacao).reversed());

        for (JsonNode registro : registros) {
            Double temperatura = paraDouble(registro, "TEM_INS");
            Double vento = paraDouble(registro, "VEN_VEL");
            if (temperatura == null && vento == null) {
                continue;
            }
            Instant dataHora = paraDataHora(registro);
            if (dataHora == null) {
                continue;
            }
            return Optional.of(new MedicaoInmetDto(
                    paraDouble(registro, "CHUVA"),
                    temperatura,
                    paraDouble(registro, "UMD_INS"),
                    vento,
                    paraDouble(registro, "VEN_RAJA"),
                    dataHora));
        }
        return Optional.empty();
    }

    /** Catalogo de todas as estacoes automaticas, filtrado pra PA -- usado so pelo seed script. */
    public List<EstacaoInmetDto> buscarEstacoesPa() {
        JsonNode resposta;
        try {
            resposta = restClient.get().uri("/estacoes/T").retrieve().body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new FonteIndisponivelException("INMET indisponível ao buscar catálogo de estações.", ex);
        }

        List<EstacaoInmetDto> estacoes = new ArrayList<>();
        if (resposta == null || !resposta.isArray()) {
            return estacoes;
        }
        for (JsonNode item : resposta) {
            if (!"PA".equals(item.path("SG_ESTADO").asText(null))) {
                continue;
            }
            try {
                String codigo = item.path("CD_ESTACAO").asText();
                String nome = item.path("DC_NOME").asText();
                double latitude = Double.parseDouble(item.path("VL_LATITUDE").asText());
                double longitude = Double.parseDouble(item.path("VL_LONGITUDE").asText());
                if (codigo.isBlank() || nome.isBlank()) {
                    throw new IllegalArgumentException("codigo/nome vazio");
                }
                estacoes.add(new EstacaoInmetDto(codigo, nome, latitude, longitude));
            } catch (RuntimeException ex) {
                log.warn("Estação do catálogo INMET ignorada (item malformado): {}", item, ex);
            }
        }
        return estacoes;
    }

    private String chaveOrdenacao(JsonNode registro) {
        return registro.path("DT_MEDICAO").asText("") + registro.path("HR_MEDICAO").asText("").strip();
    }

    private Instant paraDataHora(JsonNode registro) {
        try {
            String data = registro.path("DT_MEDICAO").asText();
            String horaBruta = registro.path("HR_MEDICAO").asText().strip();
            String hora = String.format("%04d", Integer.parseInt(horaBruta));
            LocalDateTime dataHora = LocalDateTime.parse(data + " " + hora, FORMATO_DATA_HORA);
            return dataHora.toInstant(ZoneOffset.UTC);
        } catch (Exception ex) {
            return null;
        }
    }

    private Double paraDouble(JsonNode registro, String campo) {
        JsonNode valor = registro.path(campo);
        if (valor.isMissingNode() || valor.isNull()) {
            return null;
        }
        String texto = valor.asText("").trim();
        if (texto.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(texto.replace(",", "."));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
