package com.agroclima.api.business.alerta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertaRepository extends JpaRepository<Alerta, UUID> {

    List<Alerta> findTop50ByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId);

    long countByUsuarioIdAndLidoEmIsNull(UUID usuarioId);

    Optional<Alerta> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    /** Evita repetir o mesmo aviso (rajada forte dura horas e o job roda a cada 30 minutos). */
    boolean existsByUsuarioIdAndTalhaoIdAndTipoAndCriadoEmAfter(
            UUID usuarioId, UUID talhaoId, TipoAlerta tipo, Instant depoisDe);

    @Modifying
    @Query("update Alerta a set a.lidoEm = :agora where a.usuarioId = :usuarioId and a.lidoEm is null")
    int marcarTodosLidos(@Param("usuarioId") UUID usuarioId, @Param("agora") Instant agora);

    @Modifying
    @Query("delete from Alerta a where a.criadoEm < :limite")
    int apagarAnterioresA(@Param("limite") Instant limite);
}
