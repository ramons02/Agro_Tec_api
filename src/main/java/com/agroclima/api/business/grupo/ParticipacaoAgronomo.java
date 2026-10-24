package com.agroclima.api.business.grupo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Liga um agronomo a um grupo; ele pode ter varias (spec 024, FR-001). */
@Entity
@Table(name = "agronomo_grupos")
public class ParticipacaoAgronomo {

    @Embeddable
    public record Id(@Column(name = "agronomo_id") UUID agronomoId, @Column(name = "grupo_id") UUID grupoId)
            implements Serializable {}

    @EmbeddedId
    private Id id;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected ParticipacaoAgronomo() {}

    public ParticipacaoAgronomo(UUID agronomoId, UUID grupoId, Instant agora) {
        this.id = new Id(agronomoId, grupoId);
        this.criadoEm = agora;
    }

    public UUID getAgronomoId() {
        return id.agronomoId();
    }

    public UUID getGrupoId() {
        return id.grupoId();
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
