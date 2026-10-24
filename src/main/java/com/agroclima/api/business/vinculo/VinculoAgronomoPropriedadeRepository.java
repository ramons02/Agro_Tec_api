package com.agroclima.api.business.vinculo;

import com.agroclima.api.core.base.ContagemPorUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VinculoAgronomoPropriedadeRepository extends JpaRepository<VinculoAgronomoPropriedade, UUID> {

    List<VinculoAgronomoPropriedade> findByAgronomoIdAndEstado(UUID agronomoId, EstadoVinculo estado);

    /** Quantas propriedades cada agronomo tem com vinculo no estado dado (uma consulta so para a pagina). */
    @Query("SELECT v.agronomoId AS usuarioId, COUNT(v) AS total FROM VinculoAgronomoPropriedade v "
            + "WHERE v.agronomoId IN :ids AND v.estado = :estado GROUP BY v.agronomoId")
    List<ContagemPorUsuario> contarPorAgronomo(@Param("ids") Collection<UUID> ids, @Param("estado") EstadoVinculo estado);

    /** Usado pelo job de alerta (feature 017) -- quem mais, alem do dono, recebe alerta desse talhao. */
    List<VinculoAgronomoPropriedade> findByPropriedadeIdAndEstado(UUID propriedadeId, EstadoVinculo estado);

    /** Sai do grupo: os vinculos do agronomo com propriedades daquele grupo deixam de existir (spec 024, FR-016). */
    @Modifying
    @Query("DELETE FROM VinculoAgronomoPropriedade v WHERE v.agronomoId = :agronomoId AND v.propriedadeId IN "
            + "(SELECT p.id FROM com.agroclima.api.business.propriedade.Propriedade p WHERE p.grupoId = :grupoId)")
    int apagarDoAgronomoNoGrupo(@Param("agronomoId") UUID agronomoId, @Param("grupoId") UUID grupoId);
}
