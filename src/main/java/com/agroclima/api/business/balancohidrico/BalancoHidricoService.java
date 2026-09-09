package com.agroclima.api.business.balancohidrico;

import com.agroclima.api.business.clima.clients.FontePrevisaoIndisponivelException;
import com.agroclima.api.business.clima.clients.OpenMeteoClient;
import com.agroclima.api.business.clima.clients.PrevisaoClimatica;
import com.agroclima.api.business.estacao.EstacaoInmetRepository;
import com.agroclima.api.business.estacao.EstacaoProximaProjecao;
import com.agroclima.api.business.estacao.MedicaoClimaRepository;
import com.agroclima.api.business.talhao.Talhao;
import com.agroclima.api.business.talhao.TalhaoRepository;
import com.agroclima.api.core.calculos.BalancoHidricoCalculos;
import com.agroclima.api.core.calculos.StatusPlantio;
import com.agroclima.api.core.calculos.StatusPlantioCalculos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Espelha app/services/balanco_hidrico_service.py (feature 010, RN007/RD010/RN023).
 * calcularBalancoHidricoTodosTalhoes fica pronta pra Fase 8 conectar no scheduler diario.
 */
@Service
public class BalancoHidricoService {

    private static final Logger log = LoggerFactory.getLogger(BalancoHidricoService.class);

    private final TalhaoRepository talhaoRepository;
    private final EstacaoInmetRepository estacaoInmetRepository;
    private final MedicaoClimaRepository medicaoClimaRepository;
    private final BalancoHidricoDiarioRepository balancoHidricoDiarioRepository;
    private final CulturaKcRepository culturaKcRepository;
    private final OpenMeteoClient openMeteoClient;

    public BalancoHidricoService(
            TalhaoRepository talhaoRepository,
            EstacaoInmetRepository estacaoInmetRepository,
            MedicaoClimaRepository medicaoClimaRepository,
            BalancoHidricoDiarioRepository balancoHidricoDiarioRepository,
            CulturaKcRepository culturaKcRepository,
            OpenMeteoClient openMeteoClient) {
        this.talhaoRepository = talhaoRepository;
        this.estacaoInmetRepository = estacaoInmetRepository;
        this.medicaoClimaRepository = medicaoClimaRepository;
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

        List<EstacaoProximaProjecao> estacoes =
                estacaoInmetRepository.buscarMaisProximas(talhao.getGeometria().getCentroid(), 1);
        if (estacoes.isEmpty()) {
            return Optional.empty();
        }
        String estacaoCodigo = estacoes.get(0).getCodigo();

        Instant inicioDia = dataAlvo.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant fimDia = dataAlvo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        // Sem nenhuma leitura real de precipitacao no dia (INMET fora do ar e o fallback
        // Open-Meteo nao mede chuva), nao da pra saber se choveu -- nao assume 0mm real,
        // pula o calculo do dia e mantem o ultimo armazenamento conhecido (RN007 exige
        // chuva medida, nunca prevista, RN009).
        if (!medicaoClimaRepository.existePrecipitacaoMedidaNoIntervalo(estacaoCodigo, inicioDia, fimDia)) {
            return Optional.empty();
        }

        double armAnterior = balancoHidricoDiarioRepository
                .findByTalhaoIdAndData(talhao.getId(), dataAlvo.minusDays(1))
                .map(BalancoHidricoDiario::getArmazenamentoMm)
                .orElseGet(() -> BalancoHidricoCalculos.armazenamentoInicial(cad));

        double precipitacaoMedida = medicaoClimaRepository.somarPrecipitacaoNoIntervalo(estacaoCodigo, inicioDia, fimDia);

        double et0 = 0.0;
        double chuvaPrevistaMm = 0.0;
        try {
            var centroide = talhao.getGeometria().getCentroid();
            PrevisaoClimatica previsao = openMeteoClient.obterPrevisao(centroide.getY(), centroide.getX());
            et0 = previsao.evapotranspiracaoMm();
            chuvaPrevistaMm = previsao.precipitacaoPrevistaMm();
        } catch (FontePrevisaoIndisponivelException ex) {
            log.warn("Open-Meteo indisponível pro balanço hídrico do talhão {}: {}", talhao.getId(), ex.getMessage());
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
        LocalDate hoje = LocalDate.now(ZoneOffset.UTC);
        int calculados = 0;
        for (Talhao talhao : talhaoRepository.findAll()) {
            if (calcularBalancoHidricoDoTalhao(talhao, hoje).isPresent()) {
                calculados++;
            }
        }
        return new ResumoBalancoHidrico(calculados);
    }
}
