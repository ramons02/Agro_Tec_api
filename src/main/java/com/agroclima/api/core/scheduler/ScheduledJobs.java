package com.agroclima.api.core.scheduler;

import com.agroclima.api.business.balancohidrico.BalancoHidricoService;
import com.agroclima.api.business.clima.IngestaoService;
import com.agroclima.api.business.estacao.MedicaoClimaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Espelha app/core/scheduler.py (APScheduler) -- 3 jobs. ingestao e balanco rodam
 * imediatamente no boot (via ApplicationReadyEvent) e depois no intervalo normal, igual
 * ao "next_run_time=agora" do Python; retencao NAO roda imediato (mesma diferenca do
 * Python -- so a cada 24h a partir do boot).
 */
@Component
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);
    private static final long RETENCAO_GRANULARIDADE_HORARIA_DIAS = 365;

    private final IngestaoService ingestaoService;
    private final BalancoHidricoService balancoHidricoService;
    private final MedicaoClimaRepository medicaoClimaRepository;

    public ScheduledJobs(
            IngestaoService ingestaoService,
            BalancoHidricoService balancoHidricoService,
            MedicaoClimaRepository medicaoClimaRepository) {
        this.ingestaoService = ingestaoService;
        this.balancoHidricoService = balancoHidricoService;
        this.medicaoClimaRepository = medicaoClimaRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        ingestaoInmetPeriodica();
        balancoHidricoDiario();
    }

    /** A cada 10 minutos -- ingestao_inmet_periodica. */
    @Scheduled(fixedRate = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void ingestaoInmetPeriodica() {
        var resumo = ingestaoService.ingerirTodasEstacoes();
        log.info("Ingestão INMET: sucesso={} fallback={} falha_total={}",
                resumo.estacoesComSucesso(), resumo.estacoesComFallback(), resumo.estacoesComFalhaTotal());
    }

    /** Diario -- retencao_medicoes_diaria (RNF014). */
    @Scheduled(cron = "0 0 0 * * *")
    public void retencaoMedicoesDiaria() {
        Instant limite = Instant.now().minus(RETENCAO_GRANULARIDADE_HORARIA_DIAS, ChronoUnit.DAYS);
        if (!medicaoClimaRepository.existsByDataHoraUtcBefore(limite)) {
            return;
        }
        long removidas = medicaoClimaRepository.deleteByDataHoraUtcBefore(limite);
        log.info("Retenção de medições: {} registros com mais de {} dias removidos.",
                removidas, RETENCAO_GRANULARIDADE_HORARIA_DIAS);
    }

    /** Diario -- balanco_hidrico_diario. */
    @Scheduled(cron = "0 0 0 * * *")
    public void balancoHidricoDiario() {
        var resumo = balancoHidricoService.calcularBalancoHidricoTodosTalhoes();
        log.info("Balanço hídrico diário calculado para {} talhões.", resumo.talhoesCalculados());
    }
}
