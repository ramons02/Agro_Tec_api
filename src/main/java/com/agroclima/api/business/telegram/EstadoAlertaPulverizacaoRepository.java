package com.agroclima.api.business.telegram;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EstadoAlertaPulverizacaoRepository extends JpaRepository<EstadoAlertaPulverizacao, UUID> {
}
