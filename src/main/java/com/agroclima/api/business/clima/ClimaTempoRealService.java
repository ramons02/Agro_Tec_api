package com.agroclima.api.business.clima;

import com.agroclima.api.business.estacao.EstacaoInmet;
import com.agroclima.api.business.estacao.EstacaoInmetRepository;
import com.agroclima.api.business.estacao.EstacaoProximaProjecao;
import com.agroclima.api.business.estacao.FonteDados;
import com.agroclima.api.business.estacao.MedicaoClima;
import com.agroclima.api.business.estacao.MedicaoClimaRepository;
import com.agroclima.api.business.talhao.Talhao;
import com.agroclima.api.core.calculos.IdwCalculos;
import com.agroclima.api.core.calculos.PulverizacaoCalculos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Espelha app/services/clima_tempo_real_service.py (feature 008, RN008/RN017, IDW entre
 * ate 3 estacoes -- Escopo V3/RF035). Lock por estacao so em memoria -- limitacao de
 * instancia unica, mesma nota do Python; virar lock distribuido (Redis) so se/quando
 * houver multiplas instancias.
 */
@Service
public class ClimaTempoRealService {

    private static final Duration LIMITE_STALENESS = Duration.ofMinutes(30);
    private static final int LIMITE_ESTACOES_IDW = 3;

    private final EstacaoInmetRepository estacaoInmetRepository;
    private final MedicaoClimaRepository medicaoClimaRepository;
    private final IngestaoService ingestaoService;
    private final ConcurrentHashMap<String, ReentrantLock> locksPorEstacao = new ConcurrentHashMap<>();

    public ClimaTempoRealService(
            EstacaoInmetRepository estacaoInmetRepository,
            MedicaoClimaRepository medicaoClimaRepository,
            IngestaoService ingestaoService) {
        this.estacaoInmetRepository = estacaoInmetRepository;
        this.medicaoClimaRepository = medicaoClimaRepository;
        this.ingestaoService = ingestaoService;
    }

    public record ClimaAtualResultado(
            String estacaoCodigo,
            double chuvaMm,
            double ventoKmh,
            Double rajadaKmh,
            Double temperaturaC,
            Double umidadePct,
            FonteDados fonteDados,
            Instant medidoEmUtc) {}

    private record Contribuicao(MedicaoClima medicao, FonteDados fonte, double distanciaKm) {}

    @Transactional
    public Optional<ClimaAtualResultado> obterClimaAtual(Talhao talhao) {
        List<EstacaoProximaProjecao> estacoesProximas =
                estacaoInmetRepository.buscarMaisProximas(talhao.getGeometria().getCentroid(), LIMITE_ESTACOES_IDW);
        if (estacoesProximas.isEmpty()) {
            return Optional.empty();
        }

        List<Contribuicao> contribuicoes = new ArrayList<>();
        for (EstacaoProximaProjecao estacaoProxima : estacoesProximas) {
            obterLeituraFresca(estacaoProxima.getCodigo()).ifPresent(c ->
                    contribuicoes.add(new Contribuicao(c.medicao(), c.fonte(), estacaoProxima.getDistanciaKm())));
        }
        if (contribuicoes.isEmpty()) {
            return Optional.empty();
        }

        FonteDados fonteFinal = contribuicoes.stream().allMatch(c -> c.fonte() == FonteDados.AO_VIVO)
                ? FonteDados.AO_VIVO
                : FonteDados.CACHE_EXPIRADO;
        Instant medidoEm = contribuicoes.stream()
                .map(c -> c.medicao().getDataHoraUtc())
                .min(Instant::compareTo)
                .orElseThrow();

        double chuva = interpolar(contribuicoes, c -> c.medicao().getPrecipitacaoMm());
        double ventoMs = interpolar(contribuicoes, c -> c.medicao().getVentoVelocidadeMs());
        Double rajadaMs = interpolarOuNull(contribuicoes, c -> c.medicao().getVentoRajadaMs());
        Double temperatura = interpolarOuNull(contribuicoes, c -> c.medicao().getTemperaturaC());
        Double umidade = interpolarOuNull(contribuicoes, c -> c.medicao().getUmidadePct());

        String estacaoReferencia = estacoesProximas.get(0).getCodigo();
        return Optional.of(new ClimaAtualResultado(
                estacaoReferencia,
                chuva,
                PulverizacaoCalculos.converterMsParaKmh(ventoMs),
                rajadaMs != null ? PulverizacaoCalculos.converterMsParaKmh(rajadaMs) : null,
                temperatura,
                umidade,
                fonteFinal,
                medidoEm));
    }

    private double interpolar(List<Contribuicao> contribuicoes, Function<Contribuicao, Double> extrator) {
        Double resultado = interpolarOuNull(contribuicoes, extrator);
        return resultado != null ? resultado : 0.0;
    }

    private Double interpolarOuNull(List<Contribuicao> contribuicoes, Function<Contribuicao, Double> extrator) {
        List<IdwCalculos.ValorDistancia> valores = new ArrayList<>();
        for (Contribuicao c : contribuicoes) {
            Double valor = extrator.apply(c);
            if (valor != null) {
                valores.add(new IdwCalculos.ValorDistancia(valor, Math.max(c.distanciaKm(), 0.0001)));
            }
        }
        return valores.isEmpty() ? null : IdwCalculos.interpolar(valores);
    }

    /** Double-checked locking: releitura apos adquirir o lock evita ingestao duplicada sob concorrencia. */
    private Optional<Contribuicao> obterLeituraFresca(String codigoEstacao) {
        Instant agora = Instant.now();
        Optional<MedicaoClima> medicaoAtual = medicaoClimaRepository.findFirstByEstacaoCodigoOrderByDataHoraUtcDesc(codigoEstacao);
        if (medicaoAtual.isPresent() && !estaExpirada(medicaoAtual.get(), agora)) {
            return Optional.of(new Contribuicao(medicaoAtual.get(), FonteDados.AO_VIVO, 0));
        }

        ReentrantLock lock = locksPorEstacao.computeIfAbsent(codigoEstacao, k -> new ReentrantLock());
        lock.lock();
        try {
            Optional<MedicaoClima> reChecagem =
                    medicaoClimaRepository.findFirstByEstacaoCodigoOrderByDataHoraUtcDesc(codigoEstacao);
            if (reChecagem.isPresent() && !estaExpirada(reChecagem.get(), Instant.now())) {
                return Optional.of(new Contribuicao(reChecagem.get(), FonteDados.AO_VIVO, 0));
            }
            EstacaoInmet estacao = estacaoInmetRepository.findById(codigoEstacao).orElse(null);
            if (estacao == null) {
                return Optional.empty();
            }
            ResultadoIngestao resultadoIngestao = ingestaoService.ingerirEstacao(estacao);
            Optional<MedicaoClima> aposIngestao =
                    medicaoClimaRepository.findFirstByEstacaoCodigoOrderByDataHoraUtcDesc(codigoEstacao);
            if (aposIngestao.isEmpty()) {
                return Optional.empty();
            }
            boolean fresco = resultadoIngestao != ResultadoIngestao.FALHA_TOTAL
                    && !estaExpirada(aposIngestao.get(), Instant.now());
            return Optional.of(new Contribuicao(
                    aposIngestao.get(), fresco ? FonteDados.AO_VIVO : FonteDados.CACHE_EXPIRADO, 0));
        } finally {
            lock.unlock();
        }
    }

    private boolean estaExpirada(MedicaoClima medicao, Instant agora) {
        return Duration.between(medicao.getDataHoraUtc(), agora).compareTo(LIMITE_STALENESS) > 0;
    }
}
