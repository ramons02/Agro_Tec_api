package com.agroclima.api.business.estacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface MedicaoClimaRepository extends JpaRepository<MedicaoClima, Long> {

    Optional<MedicaoClima> findFirstByEstacaoCodigoOrderByDataHoraUtcDesc(String estacaoCodigo);

    /** RNF014 -- retencao: granularidade horaria so ate 12 meses. */
    boolean existsByDataHoraUtcBefore(Instant limite);

    @Modifying
    @Transactional
    long deleteByDataHoraUtcBefore(Instant limite);

    /** Precipitacao medida (nunca prevista) somada no intervalo -- usada no Balanco Hidrico (RN007). */
    @Query("SELECT COALESCE(SUM(m.precipitacaoMm), 0.0) FROM MedicaoClima m "
            + "WHERE m.estacaoCodigo = :estacaoCodigo AND m.dataHoraUtc >= :inicio AND m.dataHoraUtc < :fim")
    double somarPrecipitacaoNoIntervalo(
            @Param("estacaoCodigo") String estacaoCodigo, @Param("inicio") Instant inicio, @Param("fim") Instant fim);

    /** Idempotente: reingestao do mesmo estacao+instante e um no-op silencioso, nao um erro. */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO medicoes_clima "
            + "(estacao_codigo, data_hora_utc, precipitacao_mm, temperatura_c, umidade_pct, "
            + " vento_velocidade_ms, vento_rajada_ms, fonte_dados) "
            + "VALUES (:estacaoCodigo, :dataHoraUtc, :precipitacaoMm, :temperaturaC, :umidadePct, "
            + " :ventoVelocidadeMs, :ventoRajadaMs, CAST(:fonteDados AS fonte_dados_medicao)) "
            + "ON CONFLICT (estacao_codigo, data_hora_utc) DO NOTHING",
            nativeQuery = true)
    void inserirSeNaoExistir(
            @Param("estacaoCodigo") String estacaoCodigo,
            @Param("dataHoraUtc") Instant dataHoraUtc,
            @Param("precipitacaoMm") Double precipitacaoMm,
            @Param("temperaturaC") Double temperaturaC,
            @Param("umidadePct") Double umidadePct,
            @Param("ventoVelocidadeMs") Double ventoVelocidadeMs,
            @Param("ventoRajadaMs") Double ventoRajadaMs,
            @Param("fonteDados") String fonteDados);
}
