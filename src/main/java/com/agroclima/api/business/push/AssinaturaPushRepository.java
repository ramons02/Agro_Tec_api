package com.agroclima.api.business.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssinaturaPushRepository extends JpaRepository<AssinaturaPush, UUID> {

    List<AssinaturaPush> findByUsuarioId(UUID usuarioId);

    Optional<AssinaturaPush> findByEndpoint(String endpoint);

    long countByUsuarioId(UUID usuarioId);

    @Modifying
    @Query("delete from AssinaturaPush a where a.endpoint = :endpoint and a.usuarioId = :usuarioId")
    int apagarDoUsuario(@Param("endpoint") String endpoint, @Param("usuarioId") UUID usuarioId);

    @Modifying
    @Query("delete from AssinaturaPush a where a.endpoint = :endpoint")
    int apagarPorEndpoint(@Param("endpoint") String endpoint);
}
