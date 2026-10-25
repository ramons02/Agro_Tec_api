package com.agroclima.api.business.propriedade;

import com.agroclima.api.core.base.ContagemPorUsuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PropriedadeRepository extends JpaRepository<Propriedade, UUID> {

    Page<Propriedade> findByIdIn(List<UUID> ids, Pageable pageable);

    /** Quantas propriedades cada um dos usuarios e dono (uma consulta so para a pagina inteira). */
    @Query("SELECT p.proprietarioId AS usuarioId, COUNT(p) AS total FROM Propriedade p "
            + "WHERE p.proprietarioId IN :ids GROUP BY p.proprietarioId")
    List<ContagemPorUsuario> contarPorProprietario(@Param("ids") Collection<UUID> ids);

    @Query("SELECT p.id FROM Propriedade p WHERE p.proprietarioId = :proprietarioId")
    List<UUID> findIdByProprietarioId(@Param("proprietarioId") UUID proprietarioId);

    /** Mudanca de grupo do dono: as propriedades dele (e, por elas, os talhoes) acompanham (spec 024, FR-003). */
    @Modifying
    @Query("UPDATE Propriedade p SET p.grupoId = :grupoId WHERE p.proprietarioId = :donoId")
    int atualizarGrupoDoDono(@Param("donoId") UUID donoId, @Param("grupoId") UUID grupoId);

    boolean existsByGrupoId(UUID grupoId);

    /** Propriedades de um grupo (Admin ve o grupo todo; spec 024). */
    @Query("SELECT p.id FROM Propriedade p WHERE p.grupoId = :grupoId")
    List<UUID> findIdByGrupoId(@Param("grupoId") UUID grupoId);

    /** Dos ids dados, os que estao em algum dos grupos (agronomo ve so o vinculado e do grupo dele). */
    @Query("SELECT p.id FROM Propriedade p WHERE p.id IN :ids AND p.grupoId IN :grupos")
    List<UUID> findIdByIdInAndGrupoIdIn(@Param("ids") Collection<UUID> ids, @Param("grupos") Collection<UUID> grupos);
}
