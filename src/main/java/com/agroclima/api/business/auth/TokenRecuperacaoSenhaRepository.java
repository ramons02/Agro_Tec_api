package com.agroclima.api.business.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TokenRecuperacaoSenhaRepository extends JpaRepository<TokenRecuperacaoSenha, UUID> {

    Optional<TokenRecuperacaoSenha> findByToken(String token);

    /** Troca de e-mail: os pedidos de recuperacao em aberto da conta deixam de valer. */
    @Modifying
    @Query("delete from TokenRecuperacaoSenha t where t.usuarioId = :usuarioId")
    void apagarDoUsuario(@Param("usuarioId") UUID usuarioId);
}
