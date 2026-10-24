package com.agroclima.api.business.balancohidrico;

import com.agroclima.api.business.clima.clients.DadosDiarios;
import com.agroclima.api.business.clima.clients.FontePrevisaoIndisponivelException;
import com.agroclima.api.business.clima.clients.OpenMeteoClient;
import com.agroclima.api.business.clima.clients.PrevisaoClimatica;
import com.agroclima.api.business.talhao.Talhao;
import com.agroclima.api.business.talhao.TalhaoRepository;
import com.agroclima.api.core.calculos.BalancoHidricoCalculos;
import com.agroclima.api.core.calculos.StatusPlantio;
import com.agroclima.api.core.calculos.StatusPlantioCalculos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Espelha app/services/balanco_hidrico_service.py (feature 010, RN007/RD010/RN023). Sem estacoes:
 * a chuva e a ET0 do dia vem da analise do modelo do Open-Meteo no ponto do talhao (dado de um dia ja
 * ocorrido, nunca previsao). O calculo roda para o dia anterior, que ja esta completo.
 */
@Service
public class BalancoHidricoService {

    private static final Logger log = LoggerFactory.getLogger(BalancoHidricoService.class);

    private final TalhaoRepository talhaoRepository;
    private final BalancoHidricoDiarioRepository balancoHidricoDiarioRepository;
    private final CulturaKcRepository culturaKcRepository;
    private final OpenMeteoClient openMeteoClient;

    public BalancoHidricoService(
            TalhaoRepository talhaoRepository,
            BalancoHidricoDiarioRepository balancoHidricoDiarioRepository,
            CulturaKcRepository culturaKcRepository,
            OpenMeteoClient openMeteoClient) {
        this.talhaoRepository = talhaoRepository;
        this.balancoHidricoDiarioRepository = balancoHidricoDiarioRepository;
        this.culturaKcRepository = culturaKcRepository;
        this.openMeteoClient = openMeteoClient;
    }

    @Transactional
    public Optional<BalancoHidricoDiario> calcularBalancoHidricoDoTalhao(Talhao talhao, LocalDate dataAlvo) {
        if (talhao.getCapacidadeAguaDisponivelMm() == null) {
            return Optional.empty();
        }
        double cad = talhao.getCapacidadeAguaDisponivelMm();

        var centroide = talhao.getGeometria().getCentroid();
        DadosDiarios dia;
        try {
            dia = openMeteoClient.obterDadosDiarios(centroide.getY(), centroide.getX(), dataAlvo);
        } catch (FontePrevisaoIndisponivelException ex) {
            log.warn("Open-Meteo indisponível pro balanço hídrico do talhão {}: {}", talhao.getId(), ex.getMessage());
            return Optional.empty();
        }
        // Sem valor de chuva do dia nao da pra saber se choveu: nao assume 0 mm, pula o calculo e
        // mantem o ultimo armazenamento conhecido (RN007 exige chuva ocorrida, nunca prevista).
        if (dia.precipitacaoMm() == null) {
            return Optional.empty();
        }

        double armAnterior = balancoHidricoDiarioRepository
                .findByTalhaoIdAndData(talhao.getId(), dataAlvo.minusDays(1))
                .map(BalancoHidricoDiario::getArmazenamentoMm)
                .orElseGet(() -> BalancoHidricoCalculos.armazenamentoInicial(cad));

        double precipitacaoMedida = dia.precipitacaoMm();
        double et0 = dia.et0Mm() != null ? dia.et0Mm() : 0.0;
        double chuvaPrevistaMm = 0.0;
        try {
            PrevisaoClimatica previsao = openMeteoClient.obterPrevisao(centroide.getY(), centroide.getX());
            chuvaPrevistaMm = previsao.precipitacaoPrevistaMm();
        } catch (FontePrevisaoIndisponivelException ex) {
            log.warn("Open-Meteo indisponível pra chuva prevista do talhão {}: {}", talhao.getId(), ex.getMessage());
        }

        double kc = obterKcDinamico(talhao, dataAlvo);
        double armazenamentoMm = BalancoHidricoCalculos.calcularArmazenamento(armAnterior, precipitacaoMedida, et0, cad, kc);
        double evapotranspiracaoReal = et0 * kc;
        StatusPlantio statusPlantio = StatusPlantioCalculos.classificarStatus(armazenamentoMm, cad, chuvaPrevistaMm);

        balancoHidricoDiarioRepository.upsert(
                UUID.randomUUID(), talhao.getId(), dataAlvo, armazenamentoMm, precipitacaoMedida,
                evapotranspiracaoReal, statusPlantio.name());

        return balancoHidricoDiarioRepository.findByTalhaoIdAndData(talhao.getId(), dataAlvo);
    }

    private double obterKcDinamico(Talhao talhao, LocalDate dataAlvo) {
        if (talhao.getCultura() == null || talhao.getDataPlantio() == null) {
            return BalancoHidricoCalculos.KC_FASE_INICIAL;
        }
        long dae = java.time.temporal.ChronoUnit.DAYS.between(talhao.getDataPlantio(), dataAlvo);
        if (dae < 0) {
            return BalancoHidricoCalculos.KC_FASE_INICIAL;
        }
        return culturaKcRepository.findPorCulturaEDae(talhao.getCultura(), (int) dae)
                .map(CulturaKc::getKcValor)
                .orElse(BalancoHidricoCalculos.KC_FASE_INICIAL);
    }

    public record ResumoBalancoHidrico(int talhoesCalculados) {}

    @Transactional
    public ResumoBalancoHidrico calcularBalancoHidricoTodosTalhoes() {
        // Dia anterior (UTC): ja completo, entao a chuva e a ET0 do dia estao fechadas no provedor.
        LocalDate ontem = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        int calculados = 0;
        for (Talhao talhao : talhaoRepository.findAll()) {
            if (calcularBalancoHidricoDoTalhao(talhao, ontem).isPresent()) {
                calculados++;
            }
        }
        return new ResumoBalancoHidrico(calculados);
    }
}
