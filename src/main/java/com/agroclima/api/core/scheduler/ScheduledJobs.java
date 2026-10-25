package com.agroclima.api.core.scheduler;

import com.agroclima.api.business.balancohidrico.BalancoHidricoService;
import com.agroclima.api.business.telegram.AlertaPulverizacaoService;
import com.agroclima.api.core.security.ArmazemJdbc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Espelha app/core/scheduler.py (APScheduler). Sem estacoes nao ha mais ingestao nem retencao de
 * medicoes: sobram o balanco hidrico diario e o alerta de pulverizacao, que rodam no boot (via
 * ApplicationReadyEvent) e depois no intervalo normal.
 */
@Component
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final BalancoHidricoService balancoHidricoService;
    private final AlertaPulverizacaoService alertaPulverizacaoService;
    private final ArmazemJdbc limitesDeTentativas;

    public ScheduledJobs(
            BalancoHidricoService balancoHidricoService,
            AlertaPulverizacaoService alertaPulverizacaoService,
            ArmazemJdbc limitesDeTentativas) {
        this.balancoHidricoService = balancoHidricoService;
        this.alertaPulverizacaoService = alertaPulverizacaoService;
        this.limitesDeTentativas = limitesDeTentativas;
    }

    /** Diario -- apaga as linhas vencidas de limite_tentativas (login, senha e cadastro). */
    @Scheduled(cron = "0 30 3 * * *")
    public void limparLimitesVencidos() {
        log.info("Limites de tentativas vencidos apagados: {}.", limitesDeTentativas.removerVencidos());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        balancoHidricoDiario();
        alertaPulverizacaoPeriodico();
    }

    /** Diario -- balanco_hidrico_diario. */
    @Scheduled(cron = "0 0 0 * * *")
    public void balancoHidricoDiario() {
        var resumo = balancoHidricoService.calcularBalancoHidricoTodosTalhoes();
        log.info("Balanço hídrico diário calculado para {} talhões.", resumo.talhoesCalculados());
    }

    /** A cada 30 minutos -- mesma cadencia de staleness do clima em tempo real (RN008),
     * feature 017 (alertas via Telegram). */
    @Scheduled(fixedRate = 30, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void alertaPulverizacaoPeriodico() {
        var resumo = alertaPulverizacaoService.processarTodosTalhoes();
        log.info("Alerta de pulverização: {} talhões processados, {} alertas enviados.",
                resumo.talhoesProcessados(), resumo.alertasEnviados());
    }
}
