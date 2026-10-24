package com.agroclima.api.business.clima;

import com.agroclima.api.business.clima.clients.InmetClient;
import com.agroclima.api.business.clima.clients.OpenMeteoClient;
import com.agroclima.api.business.clima.clients.PrevisaoClimatica;
import com.agroclima.api.business.estacao.EstacaoInmet;
import com.agroclima.api.business.estacao.EstacaoInmetRepository;
import com.agroclima.api.business.estacao.MedicaoClimaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestaoServiceInmetDesligadoTest {

    private InmetClient inmet;
    private OpenMeteoClient openMeteo;
    private MedicaoClimaRepository medicoes;
    private IngestaoService service;
    private EstacaoInmet estacao;

    @BeforeEach
    void preparar() {
        inmet = mock(InmetClient.class);
        openMeteo = mock(OpenMeteoClient.class);
        medicoes = mock(MedicaoClimaRepository.class);
        service = new IngestaoService(inmet, openMeteo, medicoes, mock(EstacaoInmetRepository.class));
        estacao = new EstacaoInmet("A201", "BELEM", "PA",
                new GeometryFactory().createPoint(new Coordinate(-48.44, -1.41)));
        when(openMeteo.obterPrevisao(anyDouble(), anyDouble())).thenReturn(new PrevisaoClimatica(
                -1.41, -48.44, 12.0, 20.0, 25.0, 27.0, 80.0, 4.0, 0.3, Map.of(), 0.0, Instant.now()));
    }

    @Test
    void comInmetDesligadoNaoChamaOInmetEVaiDiretoAoOpenMeteo() {
        when(inmet.habilitado()).thenReturn(false);

        ResultadoIngestao resultado = service.ingerirEstacao(estacao);

        assertEquals(ResultadoIngestao.FALLBACK, resultado);
        verify(inmet, never()).buscarMedicaoRecente(anyString());
        verify(openMeteo).obterPrevisao(eq(-1.41), eq(-48.44));
        verify(medicoes).inserirSeNaoExistir(eq("A201"), any(), any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void comInmetLigadoContinuaTentandoOInmetPrimeiro() {
        when(inmet.habilitado()).thenReturn(true);
        when(inmet.buscarMedicaoRecente("A201")).thenReturn(Optional.empty());

        ResultadoIngestao resultado = service.ingerirEstacao(estacao);

        assertEquals(ResultadoIngestao.FALHA_TOTAL, resultado);
        verify(inmet).buscarMedicaoRecente("A201");
        verify(openMeteo, never()).obterPrevisao(anyDouble(), anyDouble());
    }
}
