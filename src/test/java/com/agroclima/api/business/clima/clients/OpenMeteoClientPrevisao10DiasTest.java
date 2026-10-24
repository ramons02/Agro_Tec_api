package com.agroclima.api.business.clima.clients;

import com.agroclima.api.core.config.AppProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Previsao diaria da Open-Meteo (spec 023, T008): o que vem completo, o que falta e o que nao pode virar zero. */
class OpenMeteoClientPrevisao10DiasTest {

    private static final WireMockServer MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @BeforeAll
    static void iniciar() {
        MOCK.start();
    }

    @AfterAll
    static void parar() {
        MOCK.stop();
    }

    @BeforeEach
    void limpar() {
        MOCK.resetAll();
    }

    private OpenMeteoClient cliente() {
        return new OpenMeteoClient(new AppProperties(
                null, null, null, null, new AppProperties.Openmeteo(MOCK.baseUrl(), ""), null));
    }

    private void responder(String json) {
        MOCK.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(json)));
    }

    /** Cria o array JSON de {@code n} dias a partir de 2026-10-24 com valores base+i; campo opcional some se for nulo. */
    private static String corpoDiario(int dias, boolean semRajada, boolean semProbabilidade) {
        StringBuilder datas = new StringBuilder();
        for (int i = 0; i < dias; i++) {
            datas.append(i > 0 ? "," : "").append("\"").append(LocalDate.of(2026, 10, 24).plusDays(i)).append("\"");
        }
        StringBuilder daily = new StringBuilder("\"time\":[" + datas + "]");
        daily.append(",\"temperature_2m_min\":").append(serie(dias, 24));
        daily.append(",\"temperature_2m_max\":").append(serie(dias, 32));
        daily.append(",\"precipitation_sum\":").append(serie(dias, 5));
        if (!semProbabilidade) {
            daily.append(",\"precipitation_probability_max\":").append(serie(dias, 60));
        }
        daily.append(",\"wind_speed_10m_max\":").append(serie(dias, 10));
        if (!semRajada) {
            daily.append(",\"wind_gusts_10m_max\":").append(serie(dias, 25));
        }
        return "{\"daily\":{" + daily + "}}";
    }

    private static String serie(int n, int base) {
        StringBuilder s = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            s.append(i > 0 ? "," : "").append(base + i).append(".5");
        }
        return s.append("]").toString();
    }

    @Test
    void respostaCompletaDevolveOsDezDiasComTodosOsCampos() {
        responder(corpoDiario(10, false, false));

        List<PrevisaoDiaria> dias = cliente().obterPrevisao10Dias(-1.45, -48.5);

        assertEquals(10, dias.size());
        PrevisaoDiaria primeiro = dias.get(0);
        assertEquals(LocalDate.of(2026, 10, 24), primeiro.data());
        assertEquals(24.5, primeiro.temperaturaMinC());
        assertEquals(32.5, primeiro.temperaturaMaxC());
        assertEquals(5.5, primeiro.precipitacaoPrevistaMm());
        assertEquals(60.5, primeiro.probabilidadeChuvaPct());
        assertEquals(10.5, primeiro.ventoMaxKmh());
        assertEquals(25.5, primeiro.rajadaMaxKmh());
        assertEquals(LocalDate.of(2026, 11, 2), dias.get(9).data());
        assertEquals(33.5 + 8, dias.get(9).temperaturaMaxC());
        MOCK.verify(getRequestedFor(urlPathEqualTo("/v1/forecast")).withQueryParam("forecast_days", equalTo("10")));
    }

    @Test
    void campoQueAFonteNaoEntregouVemAusenteENuncaZero() {
        responder(corpoDiario(3, true, true));

        List<PrevisaoDiaria> dias = cliente().obterPrevisao10Dias(-1.45, -48.5);

        assertEquals(3, dias.size());
        for (PrevisaoDiaria dia : dias) {
            assertNull(dia.rajadaMaxKmh());
            assertNull(dia.probabilidadeChuvaPct());
            assertEquals(true, dia.ventoMaxKmh() > 0);
        }
    }

    @Test
    void valorNuloOuSerieMaisCurtaDoQueAsDatasViraAusente() {
        responder("{\"daily\":{\"time\":[\"2026-10-24\",\"2026-10-25\"],"
                + "\"temperature_2m_min\":[24.0,null],\"temperature_2m_max\":[32.0],"
                + "\"precipitation_sum\":[0.0,2.0],\"precipitation_probability_max\":[],"
                + "\"wind_speed_10m_max\":[10.0,11.0],\"wind_gusts_10m_max\":[\"x\",20.0]}}");

        List<PrevisaoDiaria> dias = cliente().obterPrevisao10Dias(-1.45, -48.5);

        assertEquals(24.0, dias.get(0).temperaturaMinC());
        assertNull(dias.get(1).temperaturaMinC());
        assertEquals(32.0, dias.get(0).temperaturaMaxC());
        assertNull(dias.get(1).temperaturaMaxC());
        // Zero que a fonte realmente mandou continua zero: so o que falta e que fica ausente.
        assertEquals(0.0, dias.get(0).precipitacaoPrevistaMm());
        assertNull(dias.get(0).probabilidadeChuvaPct());
        assertNull(dias.get(0).rajadaMaxKmh());
        assertEquals(20.0, dias.get(1).rajadaMaxKmh());
    }

    @Test
    void menosDeDezDiasDevolveSoOsQueVieram() {
        responder(corpoDiario(4, false, false));

        assertEquals(4, cliente().obterPrevisao10Dias(-1.45, -48.5).size());
    }

    @Test
    void segundaConsultaDaMesmaCoordenadaArredondadaNaoChamaAFonte() {
        responder(corpoDiario(10, false, false));
        OpenMeteoClient cliente = cliente();

        cliente.obterPrevisao10Dias(-1.4501, -48.5001);
        cliente.obterPrevisao10Dias(-1.4502, -48.5002);

        MOCK.verify(exactly(1), getRequestedFor(urlPathEqualTo("/v1/forecast")));
    }

    @Test
    void fonteComErroLancaIndisponivel() {
        MOCK.stubFor(get(urlPathEqualTo("/v1/forecast")).willReturn(aResponse().withStatus(500)));

        assertThrows(FontePrevisaoIndisponivelException.class, () -> cliente().obterPrevisao10Dias(-1.45, -48.5));
    }
}
