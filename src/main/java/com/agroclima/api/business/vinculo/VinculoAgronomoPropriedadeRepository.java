package com.agroclima.api.business.vinculo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VinculoAgronomoPropriedadeRepository extends JpaRepository<VinculoAgronomoPropriedade, UUID> {

    List<VinculoAgronomoPropriedade> findByAgronomoIdAndEstado(UUID agronomoId, EstadoVinculo estado);

    /** Usado pelo job de alerta (feature 017) -- quem mais, alem do dono, recebe alerta desse talhao. */
    List<VinculoAgronomoPropriedade> findByPropriedadeIdAndEstado(UUID propriedadeId, EstadoVinculo estado);
}
