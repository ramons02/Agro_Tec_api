package com.agroclima.api.business.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConviteGestorRepository extends JpaRepository<ConviteGestor, UUID> {

    Optional<ConviteGestor> findByTokenHash(String tokenHash);
}
