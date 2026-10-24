package com.agroclima.api.business.grupo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParticipacaoAgronomoRepository extends JpaRepository<ParticipacaoAgronomo, ParticipacaoAgronomo.Id> {

    List<ParticipacaoAgronomo> findByIdAgronomoId(UUID agronomoId);

    long countByIdAgronomoId(UUID agronomoId);

    List<ParticipacaoAgronomo> findByIdGrupoId(UUID grupoId);

    long countByIdGrupoId(UUID grupoId);

    List<ParticipacaoAgronomo> findByIdAgronomoIdIn(java.util.Collection<UUID> agronomoIds);
}
