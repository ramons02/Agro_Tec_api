package com.agroclima.api.business.grupo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GrupoRepository extends JpaRepository<Grupo, UUID> {

    Optional<Grupo> findByAdminId(UUID adminId);

    Optional<Grupo> findByCodigoCadastroHash(String codigoCadastroHash);

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);

    /** O grupo mais antigo e o grupo inicial criado pela migracao (fase 1 da spec 024). */
    Optional<Grupo> findFirstByOrderByCriadoEmAscIdAsc();
}
