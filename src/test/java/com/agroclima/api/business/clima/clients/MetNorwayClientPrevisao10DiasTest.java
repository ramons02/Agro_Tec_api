package com.agroclima.api.business.clima.clients;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Reserva MET Norway da previsao de 10 dias (spec 023, T011): agregacao por dia UTC e o que fica ausente. */
class MetNorwayClientPrevisao10DiasTest {

    private static final String CAMINHO = "/weatherapi/locationforecast/2.0/complete";
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

    private MetNorwayClient cliente() {
        return new MetNorwayClient(MOCK.baseUrl(), "AgroTeste/1.0 (teste@exemplo.com)");
    }

    private void responder(String json) {
        MOCK.stubFor(get(urlPathEqualTo(CAMINHO)).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(json)));
    }

    /** Um ponto da serie; {@code bloco} e o trecho de chuva ("next_1_hours" ou "next_6_hours") ou vazio. */
    private static String ponto(String horario, Double temperaturaC, Double ventoMs, String bloco, Double chuvaMm) {
        StringBuilder detalhes = new StringBuilder();
        if (temperaturaC != null) {
            detalhes.append("\"air_temperature\":").append(temperaturaC);
        }
        if (ventoMs != null) {
            detalhes.append(detalhes.length() > 0 ? "," : "").append("\"wind_speed\":").append(ventoMs);
        }
        String chuva = bloco == null ? ""
                : ",\"" + bloco + "\":{\"details\":{\"precipitation_amount\":" + chuvaMm + "}}";
        return "{\"time\":\"" + horario + "\",\"data\":{\"instant\":{\"details\":{" + detalhes + "}}" + chuva + "}}";
    }

    private static String serie(String... pontos) {
        return "{\"properties\":{\"timeseries\":[" + String.join(",", pontos) + "]}}";
    }

    @Test
    void agregaPorDiaMinimaMaximaChuvaEVentoMaximoEmKmh() {
        responder(serie(
                ponto("2026-10-24T18:00:00Z", 30.0, 2.0, "next_1_hours", 0.4),
                ponto("2026-10-24T19:00:00Z", 28.0, 5.0, "next_1_hours", 1.1),
                ponto("2026-10-24T20:00:00Z", 26.5, 3.0, "next_1_hours", 0.0),
                ponto("2026-10-25T00:00:00Z", 25.0, 1.0, "next_6_hours", 3.0),
                ponto("2026-10-25T06:00:00Z", 24.0, 2.0, "next_6_hours", 0.5),
                ponto("2026-10-25T12:00:00Z", 31.0, 4.0, "next_6_hours", 2.0)));

        List<PrevisaoDiaria> dias = cliente().obterPrevisao10Dias(-1.45, -48.5);

        assertEquals(2, dias.size());
        PrevisaoDiaria d1 = dias.get(0);
        assertEquals(LocalDate.of(2026, 10, 24), d1.data());
        assertEquals(26.5, d1.temperaturaMinC());
        assertEquals(30.0, d1.temperaturaMaxC());
        assertEquals(1.5, d1.precipitacaoPrevistaMm(), 1e-9);
        assertEquals(18.0, d1.ventoMaxKmh(), 1e-9);
        PrevisaoDiaria d2 = dias.get(1);
        assertEquals(24.0, d2.temperaturaMinC());
        assertEquals(31.0, d2.temperaturaMaxC());
        assertEquals(5.5, d2.precipitacaoPrevistaMm(), 1e-9);
        assertEquals(14.4, d2.ventoMaxKmh(), 1e-9);
    }

    @Test
    void rajadaEProbabilidadeDeChuvaFicamAusentesNuncaZero() {
        responder(serie(
                ponto("2026-10-24T00:00:00Z", 25.0, 2.0, "next_6_hours", 0.0),
                ponto("2026-10-24T06:00:00Z", 26.0, 2.0, "next_6_hours", 0.0),
                ponto("2026-10-24T12:00:00Z", 27.0, 2.0, "next_6_hours", 0.0)));

        PrevisaoDiaria dia = cliente().obterPrevisao10Dias(-1.45, -48.5).get(0);

        assertNull(dia.rajadaMaxKmh());
        assertNull(dia.probabilidadeChuvaPct());
        // Chuva zero que a fonte mandou e zero; o que falta e que fica ausente.
        assertEquals(0.0, dia.precipitacaoPrevistaMm());
    }

    @Test
    void semBlocoDeChuvaAChuvaDoDiaFicaAusente() {
        responder(serie(
                ponto("2026-10-24T00:00:00Z", 25.0, 2.0, null, null),
                ponto("2026-10-24T06:00:00Z", 26.0, 2.0, null, null),
                ponto("2026-10-24T12:00:00Z", 27.0, 2.0, null, null)));

        assertNull(cliente().obterPrevisao10Dias(-1.45, -48.5).get(0).precipitacaoPrevistaMm());
    }

    @Test
    void diaComMenosDeTresPontosEDescartado() {
        responder(serie(
                ponto("2026-10-24T22:00:00Z", 25.0, 2.0, "next_1_hours", 0.0),
                ponto("2026-10-24T23:00:00Z", 24.0, 2.0, "next_1_hours", 0.0),
                ponto("2026-10-25T00:00:00Z", 24.0, 2.0, "next_6_hours", 0.0),
                ponto("2026-10-25T06:00:00Z", 24.0, 2.0, "next_6_hours", 0.0),
                ponto("2026-10-25T12:00:00Z", 24.0, 2.0, "next_6_hours", 0.0)));

        List<PrevisaoDiaria> dias = cliente().obterPrevisao10Dias(-1.45, -48.5);

        assertEquals(1, dias.size());
        assertEquals(LocalDate.of(2026, 10, 25), dias.get(0).data());
    }

    @Test
    void naoPassaDeDezDias() {
        String[] pontos = new String[12 * 3];
        for (int d = 0; d < 12; d++) {
            for (int h = 0; h < 3; h++) {
                pontos[d * 3 + h] = ponto(LocalDate.of(2026, 10, 24).plusDays(d) + "T" + (h * 6 < 10 ? "0" : "") + (h * 6) + ":00:00Z",
                        25.0, 2.0, "next_6_hours", 0.0);
            }
        }
        responder(serie(pontos));

        List<PrevisaoDiaria> dias = cliente().obterPrevisao10Dias(-1.45, -48.5);

        assertEquals(10, dias.size());
        assertEquals(LocalDate.of(2026, 11, 2), dias.get(9).data());
    }

    @Test
    void campoDeTemperaturaAusenteNoDiaViraNulo() {
        responder(serie(
                ponto("2026-10-24T00:00:00Z", null, 2.0, "next_6_hours", 0.0),
                ponto("2026-10-24T06:00:00Z", null, 2.0, "next_6_hours", 0.0),
                ponto("2026-10-24T12:00:00Z", null, 2.0, "next_6_hours", 0.0)));

        PrevisaoDiaria dia = cliente().obterPrevisao10Dias(-1.45, -48.5).get(0);

        assertNull(dia.temperaturaMinC());
        assertNull(dia.temperaturaMaxC());
    }

    @Test
    void erroDaFonteOuSerieVaziaLancaIndisponivel() {
        MOCK.stubFor(get(urlPathEqualTo(CAMINHO)).willReturn(aResponse().withStatus(503)));
        assertThrows(FontePrevisaoIndisponivelException.class, () -> cliente().obterPrevisao10Dias(-1.45, -48.5));

        MOCK.resetAll();
        responder(serie());
        assertThrows(FontePrevisaoIndisponivelException.class, () -> cliente().obterPrevisao10Dias(-1.45, -48.5));

        MOCK.resetAll();
        responder("{}");
        assertThrows(FontePrevisaoIndisponivelException.class, () -> cliente().obterPrevisao10Dias(-1.45, -48.5));
    }

    @Test
    void segundaConsultaDaMesmaCoordenadaArredondadaNaoChamaAFonte() {
        responder(serie(
                ponto("2026-10-24T00:00:00Z", 25.0, 2.0, "next_6_hours", 0.0),
                ponto("2026-10-24T06:00:00Z", 26.0, 2.0, "next_6_hours", 0.0),
                ponto("2026-10-24T12:00:00Z", 27.0, 2.0, "next_6_hours", 0.0)));
        MetNorwayClient cliente = cliente();

        cliente.obterPrevisao10Dias(-1.4501, -48.5001);
        cliente.obterPrevisao10Dias(-1.4502, -48.5002);

        MOCK.verify(exactly(1), getRequestedFor(urlPathEqualTo(CAMINHO)));
    }
}
