package com.agroclima.api.business.propriedade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PropriedadeRepository extends JpaRepository<Propriedade, UUID> {

    Page<Propriedade> findByIdIn(List<UUID> ids, Pageable pageable);

    @Query("SELECT p.id FROM Propriedade p WHERE p.proprietarioId = :proprietarioId")
    List<UUID> findIdByProprietarioId(@Param("proprietarioId") UUID proprietarioId);
}
