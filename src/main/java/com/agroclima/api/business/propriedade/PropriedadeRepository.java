package com.agroclima.api.business.propriedade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PropriedadeRepository extends JpaRepository<Propriedade, UUID> {

    Page<Propriedade> findByIdIn(List<UUID> ids, Pageable pageable);

    List<UUID> findIdByProprietarioId(UUID proprietarioId);
}
