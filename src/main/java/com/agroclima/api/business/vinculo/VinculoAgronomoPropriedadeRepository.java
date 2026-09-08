package com.agroclima.api.business.vinculo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VinculoAgronomoPropriedadeRepository extends JpaRepository<VinculoAgronomoPropriedade, UUID> {

    List<VinculoAgronomoPropriedade> findByAgronomoIdAndEstado(UUID agronomoId, EstadoVinculo estado);
}
