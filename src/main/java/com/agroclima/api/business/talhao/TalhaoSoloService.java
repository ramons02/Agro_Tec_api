package com.agroclima.api.business.talhao;

import com.agroclima.api.business.balancohidrico.BalancoHidricoService;
import com.agroclima.api.business.clima.clients.FonteSoloIndisponivelException;
import com.agroclima.api.business.clima.clients.PerfilSolo;
import com.agroclima.api.business.clima.clients.SoilGridsClient;
import com.agroclima.api.core.calculos.SoloCalculos;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Busca no SoilGrids o perfil do solo do talhao (textura e CAD) fora da requisicao de cadastro. O SoilGrids
 * leva de 30 s a mais de 1 min, entao o cadastro responde na hora e o solo chega depois: logo apos o commit
 * (evento) e, como rede de seguranca, pelo job periodico que pega quem ficou sem solo. RN017: nunca bloqueia
 * nem derruba o talhao.
 */
@Service
public class TalhaoSoloService {

    private static final Logger log = LoggerFactory.getLogger(TalhaoSoloService.class);
    private static final int MAXIMO_POR_RODADA = 5;

    private final TalhaoRepository talhaoRepository;
    private final SoilGridsClient soilGridsClient;
    private final BalancoHidricoService balancoHidricoService;
    private final TransactionTemplate transacao;
    private final boolean ativo;
    private final int tentativas;
    private final long pausaEntreTentativasMs;

    private final Set<UUID> emAndamento = ConcurrentHashMap.newKeySet();
    /** Local sem dado no SoilGrids (resposta legitima): nao adianta perguntar de novo a cada rodada. */
    private final Set<UUID> semCobertura = ConcurrentHashMap.newKeySet();

    public TalhaoSoloService(
            TalhaoRepository talhaoRepository,
            SoilGridsClient soilGridsClient,
            BalancoHidricoService balancoHidricoService,
            PlatformTransactionManager gerenciadorTransacao,
            @Value("${agroclima.solo.segundo-plano:true}") boolean ativo,
            @Value("${agroclima.solo.tentativas:3}") int tentativas,
            @Value("${agroclima.solo.pausa-entre-tentativas-ms:5000}") long pausaEntreTentativasMs) {
        this.talhaoRepository = talhaoRepository;
        this.soilGridsClient = soilGridsClient;
        this.balancoHidricoService = balancoHidricoService;
        this.transacao = new TransactionTemplate(gerenciadorTransacao);
        this.ativo = ativo;
        this.tentativas = tentativas;
        this.pausaEntreTentativasMs = pausaEntreTentativasMs;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoPrecisarDeSolo(TalhaoPrecisaDeSolo evento) {
        if (ativo) {
            completar(evento.talhaoId());
        }
    }

    public boolean segundoPlanoAtivo() {
        return ativo;
    }

    /** Job periodico: completa os talhoes que ainda estao sem solo. Devolve quantos foram completados. */
    public int completarPendentes() {
        List<Talhao> pendentes = talhaoRepository.findByTipoSoloIsNull().stream()
                .filter(t -> !semCobertura.contains(t.getId()))
                .limit(MAXIMO_POR_RODADA)
                .toList();
        int completados = 0;
        for (Talhao talhao : pendentes) {
            if (completar(talhao.getId())) {
                completados++;
            }
        }
        return completados;
    }

    /** Busca e grava o solo do talhao e ja calcula o balanco hidrico do dia. Verdadeiro se gravou o solo. */
    public boolean completar(UUID talhaoId) {
        if (!emAndamento.add(talhaoId)) {
            return false;
        }
        try {
            Optional<Talhao> encontrado = talhaoRepository.findById(talhaoId);
            if (encontrado.isEmpty() || encontrado.get().getTipoSolo() != null) {
                return false;
            }
            Point centroide = encontrado.get().getGeometria().getCentroid();

            Optional<PerfilSolo> perfil;
            try {
                perfil = buscarComTentativas(centroide);
            } catch (FonteSoloIndisponivelException ex) {
                log.warn("SoilGrids indisponível para o talhão {}; o job tenta de novo: {}", talhaoId, ex.getMessage());
                return false;
            }
            if (perfil.isEmpty()) {
                log.warn("SoilGrids sem cobertura para o talhão {} em ({}, {})",
                        talhaoId, centroide.getY(), centroide.getX());
                semCobertura.add(talhaoId);
                return false;
            }

            Optional<Talhao> gravado = transacao.execute(status -> gravar(talhaoId, perfil.get()));
            if (gravado.isEmpty()) {
                return false;
            }
            calcularBalancoDeOntem(gravado.get());
            return true;
        } finally {
            emAndamento.remove(talhaoId);
        }
    }

    private Optional<Talhao> gravar(UUID talhaoId, PerfilSolo perfil) {
        Optional<Talhao> atual = talhaoRepository.findById(talhaoId);
        if (atual.isEmpty() || atual.get().getTipoSolo() != null) {
            return Optional.empty();
        }
        Talhao talhao = atual.get();
        var tipoSolo = SoloCalculos.classificarTextura(
                perfil.fracaoArgilaPct(), perfil.fracaoAreiaPct(), perfil.fracaoSiltePct());
        var ccPmp = SoloCalculos.estimarCcPmp(
                perfil.fracaoArgilaPct(), perfil.fracaoAreiaPct(), perfil.materiaOrganicaPct());
        double cad = SoloCalculos.calcularCad(ccPmp.ccPct(), ccPmp.pmpPct(), perfil.densidadeSoloGCm3());
        talhao.parametrizarSolo(tipoSolo, perfil.fracaoArgilaPct(), perfil.fracaoAreiaPct(),
                perfil.fracaoSiltePct(), perfil.materiaOrganicaPct(), cad);
        return Optional.of(talhaoRepository.save(talhao));
    }

    private void calcularBalancoDeOntem(Talhao talhao) {
        try {
            balancoHidricoService.calcularBalancoHidricoDoTalhao(talhao, LocalDate.now(ZoneOffset.UTC).minusDays(1));
        } catch (RuntimeException ex) {
            // O job diario recalcula; o solo ja foi gravado e isso nao pode ser desfeito por falha do clima.
            log.warn("Balanço hídrico inicial do talhão {} não calculado: {}", talhao.getId(), ex.getMessage());
        }
    }

    private Optional<PerfilSolo> buscarComTentativas(Point centroide) {
        FonteSoloIndisponivelException ultima = null;
        for (int tentativa = 1; tentativa <= tentativas; tentativa++) {
            try {
                return soilGridsClient.parametrizarSolo(centroide.getY(), centroide.getX());
            } catch (FonteSoloIndisponivelException ex) {
                ultima = ex;
                if (tentativa < tentativas) {
                    pausar();
                }
            }
        }
        throw ultima != null ? ultima : new FonteSoloIndisponivelException("SoilGrids indisponível.", null);
    }

    private void pausar() {
        try {
            Thread.sleep(pausaEntreTentativasMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
