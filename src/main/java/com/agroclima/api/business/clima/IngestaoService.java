package com.agroclima.api.business.clima;

import com.agroclima.api.business.clima.clients.FonteIndisponivelException;
import com.agroclima.api.business.clima.clients.FontePrevisaoIndisponivelException;
import com.agroclima.api.business.clima.clients.InmetClient;
import com.agroclima.api.business.clima.clients.MedicaoInmetDto;
import com.agroclima.api.business.clima.clients.MetNorwayClient;
import com.agroclima.api.business.clima.clients.OpenMeteoClient;
import com.agroclima.api.business.clima.clients.PrevisaoClimatica;
import com.agroclima.api.business.estacao.EstacaoInmet;
import com.agroclima.api.business.estacao.EstacaoInmetRepository;
import com.agroclima.api.business.estacao.FonteDados;
import com.agroclima.api.business.estacao.MedicaoClimaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Espelha app/services/ingestao_service.py (feature 002, RN009 -- fallback/circuit breaker). */
@Service
public class IngestaoService {

    private static final Logger log = LoggerFactory.getLogger(IngestaoService.class);

    private final InmetClient inmetClient;
    private final OpenMeteoClient openMeteoClient;
    private final MetNorwayClient metNorwayClient;
    private final MedicaoClimaRepository medicaoClimaRepository;
    private final EstacaoInmetRepository estacaoInmetRepository;

    public IngestaoService(
            InmetClient inmetClient,
            OpenMeteoClient openMeteoClient,
            MetNorwayClient metNorwayClient,
            MedicaoClimaRepository medicaoClimaRepository,
            EstacaoInmetRepository estacaoInmetRepository) {
        this.inmetClient = inmetClient;
        this.openMeteoClient = openMeteoClient;
        this.metNorwayClient = metNorwayClient;
        this.medicaoClimaRepository = medicaoClimaRepository;
        this.estacaoInmetRepository = estacaoInmetRepository;
    }

    @Transactional
    public ResultadoIngestao ingerirEstacao(EstacaoInmet estacao) {
        if (!inmetClient.habilitado()) {
            return tentarFallbackOpenMeteo(estacao);
        }
        Optional<MedicaoInmetDto> medicaoOpt;
        try {
            medicaoOpt = inmetClient.buscarMedicaoRecente(estacao.getCodigo());
        } catch (FonteIndisponivelException ex) {
            log.warn("INMET indisponível para {}: {}", estacao.getCodigo(), ex.getMessage());
            return tentarFallbackOpenMeteo(estacao);
        }
        if (medicaoOpt.isEmpty()) {
            return ResultadoIngestao.FALHA_TOTAL;
        }
        MedicaoInmetDto medicao = medicaoOpt.get();
        medicaoClimaRepository.inserirSeNaoExistir(
                estacao.getCodigo(), medicao.dataHoraUtc(), medicao.precipitacaoMm(), medicao.temperaturaC(),
                medicao.umidadePct(), medicao.ventoVelocidadeMs(), medicao.ventoRajadaMs(), FonteDados.AO_VIVO.name());
        return ResultadoIngestao.SUCESSO;
    }

    /** Cadeia de reserva sem INMET: Open-Meteo primeiro, MET Norway se ele falhar; ambos viram PREVISAO. */
    private ResultadoIngestao tentarFallbackOpenMeteo(EstacaoInmet estacao) {
        double lat = estacao.getPosicao().getY();
        double lon = estacao.getPosicao().getX();
        try {
            PrevisaoClimatica previsao = openMeteoClient.obterPrevisao(lat, lon);
            medicaoClimaRepository.inserirSeNaoExistir(
                    estacao.getCodigo(), previsao.obtidoEmUtc(), null, previsao.temperatura2mC(),
                    previsao.umidadeAr2mPct(), previsao.vento10mKmh() / 3.6, previsao.rajada10mKmh() / 3.6,
                    FonteDados.PREVISAO.name());
            return ResultadoIngestao.FALLBACK;
        } catch (FontePrevisaoIndisponivelException ex) {
            log.warn("Open-Meteo indisponível para {}: {} -- tentando MET Norway.", estacao.getCodigo(), ex.getMessage());
        }
        try {
            MetNorwayClient.CondicaoAtual condicao = metNorwayClient.obterCondicaoAtual(lat, lon);
            // Sem rajada: o MET Norway nao a fornece nos tropicos (fica nula, como o INMET podia deixar).
            medicaoClimaRepository.inserirSeNaoExistir(
                    estacao.getCodigo(), condicao.obtidoEmUtc(), condicao.precipitacaoMm(), condicao.temperaturaC(),
                    condicao.umidadePct(), condicao.ventoMs(), null, FonteDados.PREVISAO.name());
            return ResultadoIngestao.FALLBACK;
        } catch (FontePrevisaoIndisponivelException ex) {
            log.error("Open-Meteo e MET Norway falharam para {}: {}", estacao.getCodigo(), ex.getMessage());
            return ResultadoIngestao.FALHA_TOTAL;
        }
    }

    public record ResumoIngestao(int estacoesComSucesso, int estacoesComFallback, int estacoesComFalhaTotal) {}

    @Transactional
    public ResumoIngestao ingerirTodasEstacoes() {
        List<EstacaoInmet> estacoes = estacaoInmetRepository.findAll();
        int sucesso = 0;
        int fallback = 0;
        int falhaTotal = 0;
        for (EstacaoInmet estacao : estacoes) {
            ResultadoIngestao resultado = ingerirEstacao(estacao);
            switch (resultado) {
                case SUCESSO -> sucesso++;
                case FALLBACK -> fallback++;
                case FALHA_TOTAL -> falhaTotal++;
            }
        }
        return new ResumoIngestao(sucesso, fallback, falhaTotal);
    }
}
