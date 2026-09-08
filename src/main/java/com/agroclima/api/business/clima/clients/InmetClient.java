package com.agroclima.api.business.clima.clients;

import com.agroclima.api.core.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
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
 * buscarEstacoesPa() (catalogo de estacoes, usado so pelo seed script) fica pra Fase 8 --
 * nao e necessario pro fluxo de ingestao/clima em tempo real desta fase.
 */
@Component
public class InmetClient {

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
