package com.agroclima.api.business.propriedade;

import com.agroclima.api.core.base.ContagemPorUsuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
