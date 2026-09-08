package com.agroclima.api.business.balancohidrico;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface BalancoHidricoDiarioRepository extends JpaRepository<BalancoHidricoDiario, UUID> {

    Optional<BalancoHidricoDiario> findFirstByTalhaoIdOrderByDataDesc(UUID talhaoId);

    Optional<BalancoHidricoDiario> findByTalhaoIdAndData(UUID talhaoId, LocalDate data);

    /** Upsert idempotente por talhao+dia -- recalculo do mesmo dia so atualiza, nunca duplica. */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO balanco_hidrico_diario "
            + "(id, talhao_id, data, armazenamento_mm, precipitacao_mm, evapotranspiracao_mm, status_plantio) "
            + "VALUES (:id, :talhaoId, :data, :armazenamentoMm, :precipitacaoMm, :evapotranspiracaoMm, :statusPlantio) "
            + "ON CONFLICT (talhao_id, data) DO UPDATE SET "
            + "armazenamento_mm = EXCLUDED.armazenamento_mm, "
            + "precipitacao_mm = EXCLUDED.precipitacao_mm, "
            + "evapotranspiracao_mm = EXCLUDED.evapotranspiracao_mm, "
            + "status_plantio = EXCLUDED.status_plantio",
            nativeQuery = true)
    void upsert(
            @Param("id") UUID id,
            @Param("talhaoId") UUID talhaoId,
            @Param("data") LocalDate data,
            @Param("armazenamentoMm") double armazenamentoMm,
            @Param("precipitacaoMm") double precipitacaoMm,
            @Param("evapotranspiracaoMm") double evapotranspiracaoMm,
            @Param("statusPlantio") String statusPlantio);
}
