package com.agroclima.api.business.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    long countByPapel(Papel papel);

    long countByCriadoEmAfter(Instant instante);

    Optional<Usuario> findFirstByOrderByCriadoEmDesc();

    long countByStatus(StatusConta status);

    long countByGrupoId(UUID grupoId);

    List<Usuario> findByGrupoIdAndPapel(UUID grupoId, Papel papel);

    long countBySenhaEsquecidaEmIsNotNull();

    long countByPapelAndStatus(Papel papel, StatusConta status);

    /** Bloqueia a linha ate o fim da transacao: duas acoes de gestao na mesma conta nao se atropelam (R6). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> buscarParaAtualizar(@Param("id") UUID id);
}
