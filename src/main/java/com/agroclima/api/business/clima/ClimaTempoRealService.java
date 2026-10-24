package com.agroclima.api.business.clima;

import com.agroclima.api.business.clima.clients.CondicaoAtual;
import com.agroclima.api.business.clima.clients.FontePrevisaoIndisponivelException;
import com.agroclima.api.business.clima.clients.MetNorwayClient;
import com.agroclima.api.business.clima.clients.OpenMeteoClient;
import com.agroclima.api.business.talhao.Talhao;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Clima atual de um talhao no centroide dele (RF033, RN008, RN017). Sem estacoes: Open-Meteo
 * primeiro; se falhar, MET Norway; se os dois falharem, a ultima leitura boa daquele ponto (ate
 * 6 h) volta marcada CACHE_EXPIRADO -- nunca erro bloqueante quando ja houve uma leitura (RN017).
 * E sempre valor de modelo, nunca medicao de estacao.
 */
@Service
public class ClimaTempoRealService {

    private static final Logger log = LoggerFactory.getLogger(ClimaTempoRealService.class);
    private static final Duration LIMITE_CACHE_EXPIRADO = Duration.ofHours(6);

    public record ClimaAtualResultado(
            double chuvaMm,
            double ventoKmh,
            Double rajadaKmh,
            Double temperaturaC,
            Double umidadePct,
            FonteDados fonteDados,
            Instant medidoEmUtc) {}

    private final OpenMeteoClient openMeteoClient;
    private final MetNorwayClient metNorwayClient;
    private final Cache<String, CondicaoAtual> ultimaLeituraBoa =
            Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(LIMITE_CACHE_EXPIRADO).build();

    public ClimaTempoRealService(OpenMeteoClient openMeteoClient, MetNorwayClient metNorwayClient) {
        this.openMeteoClient = openMeteoClient;
        this.metNorwayClient = metNorwayClient;
    }

    /** Para testes: o cache e de singleton e atravessa testes que compartilham o contexto Spring. */
    public void limparCache() {
        ultimaLeituraBoa.invalidateAll();
    }

    public Optional<ClimaAtualResultado> obterClimaAtual(Talhao talhao) {
        Point centroide = talhao.getGeometria().getCentroid();
        double lat = centroide.getY();
        double lon = centroide.getX();
        String chave = Math.round(lat * 100) / 100.0 + "|" + Math.round(lon * 100) / 100.0;

        Optional<CondicaoAtual> fresca = buscarCondicao(lat, lon);
        if (fresca.isPresent()) {
            ultimaLeituraBoa.put(chave, fresca.get());
            return Optional.of(paraResultado(fresca.get(), FonteDados.PREVISAO));
        }
        return Optional.ofNullable(ultimaLeituraBoa.getIfPresent(chave))
                .map(antiga -> paraResultado(antiga, FonteDados.CACHE_EXPIRADO));
    }

    private Optional<CondicaoAtual> buscarCondicao(double lat, double lon) {
        try {
            return Optional.of(openMeteoClient.obterCondicaoAtual(lat, lon));
        } catch (FontePrevisaoIndisponivelException ex) {
            log.warn("Open-Meteo indisponível para ({}, {}): {} -- tentando MET Norway.", lat, lon, ex.getMessage());
        }
        try {
            return Optional.of(metNorwayClient.obterCondicaoAtual(lat, lon));
        } catch (FontePrevisaoIndisponivelException ex) {
            log.error("Open-Meteo e MET Norway indisponíveis para ({}, {}): {}", lat, lon, ex.getMessage());
            return Optional.empty();
        }
    }

    private ClimaAtualResultado paraResultado(CondicaoAtual c, FonteDados fonte) {
        return new ClimaAtualResultado(
                c.precipitacaoMm() != null ? c.precipitacaoMm() : 0.0,
                c.ventoKmh(),
                c.rajadaKmh(),
                c.temperaturaC(),
                c.umidadePct(),
                fonte,
                c.obtidoEmUtc());
    }
}
