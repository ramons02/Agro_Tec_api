package com.agroclima.api.business.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditoriaContaRepository extends JpaRepository<AuditoriaConta, UUID> {

    List<AuditoriaConta> findByAlvoIdOrderByCriadoEmDesc(UUID alvoId);

    List<AuditoriaConta> findByAlvoEmailIgnoreCaseOrderByCriadoEmDesc(String alvoEmail);
}
