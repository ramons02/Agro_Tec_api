package com.agroclima.api.business.telegram;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TokenVinculoTelegramRepository extends JpaRepository<TokenVinculoTelegram, UUID> {

    Optional<TokenVinculoTelegram> findByToken(String token);
}
