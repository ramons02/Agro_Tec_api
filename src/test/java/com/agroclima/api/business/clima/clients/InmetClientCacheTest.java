package com.agroclima.api.business.clima.clients;

import com.agroclima.api.core.cache.CacheRedisService;
import com.agroclima.api.core.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato do cache Redis (spec 018, T008): com Redis disponível a segunda consulta à mesma
 * estação não repete a chamada real ao INMET dentro do TTL; sem Redis, degrada chamando a
 * fonte real de novo a cada vez, sem erro. Código de estação aleatório por teste -- roda contra
 * um Redis real e compartilhado (mesmo REDIS_URL do CI), uma chave fixa colidiria entre execuções.
 */
class InmetClientCacheTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private WireMockServer inmetMock;

    @BeforeEach
    void iniciar() {
        inmetMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        inmetMock.start();
    }

    @AfterEach
    void parar() {
        inmetMock.stop();
    }

    private InmetClient cliente(String redisUrl) {
        AppProperties propriedades = new AppProperties(
                null, null, null, null,
                new AppProperties.Inmet(inmetMock.baseUrl()), null, null,
                new AppProperties.Redis(redisUrl));
        CacheRedisService cache = new CacheRedisService(propriedades);
        return new InmetClient(propriedades, cache, OBJECT_MAPPER);
    }

    private void stubInmetComLeitura() {
        ZonedDateTime agoraUtc = ZonedDateTime.now(ZoneOffset.UTC);
        String data = agoraUtc.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String hora = agoraUtc.format(DateTimeFormatter.ofPattern("HHmm"));
        String corpo = "["
                + "{\"DT_MEDICAO\":\"" + data + "\",\"HR_MEDICAO\":\"" + hora + "\",\"CHUVA\":\"1.0\","
                + "\"TEM_INS\":\"28.0\",\"UMD_INS\":\"70\",\"VEN_VEL\":\"2.0\",\"VEN_RAJA\":\"4.0\"}"
                + "]";
        inmetMock.stubFor(get(urlPathMatching("/estacao/dados/.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(corpo)));
    }

    @Test
    void comRedisDisponivelSegundaConsultaNaoRepeteChamadaReal() {
        String redisUrl = System.getenv().getOrDefault("REDIS_URL", "redis://localhost:6379");
        InmetClient cliente = cliente(redisUrl);
        String codigoEstacao = "TESTE-" + UUID.randomUUID();
        stubInmetComLeitura();

        Optional<MedicaoInmetDto> primeira = cliente.buscarMedicaoRecente(codigoEstacao);
        Optional<MedicaoInmetDto> segunda = cliente.buscarMedicaoRecente(codigoEstacao);

        assertThat(primeira).isPresent();
        assertThat(segunda).isEqualTo(primeira);
        inmetMock.verify(1, getRequestedFor(urlPathMatching("/estacao/dados/.*")));
    }

    @Test
    void semRedisCadaConsultaRepeteChamadaRealSemErro() {
        InmetClient cliente = cliente("");
        String codigoEstacao = "TESTE-" + UUID.randomUUID();
        stubInmetComLeitura();

        Optional<MedicaoInmetDto> primeira = cliente.buscarMedicaoRecente(codigoEstacao);
        Optional<MedicaoInmetDto> segunda = cliente.buscarMedicaoRecente(codigoEstacao);

        assertThat(primeira).isPresent();
        assertThat(segunda).isPresent();
        inmetMock.verify(2, getRequestedFor(urlPathMatching("/estacao/dados/.*")));
    }
}
