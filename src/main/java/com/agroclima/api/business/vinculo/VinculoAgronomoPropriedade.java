package com.agroclima.api.business.vinculo;

import com.agroclima.api.core.base.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vinculos_agronomo_propriedade")
public class VinculoAgronomoPropriedade extends BaseModel<UUID> {

    @Id
    private UUID id;

    @Column(name = "agronomo_id", nullable = false)
    private UUID agronomoId;

    @Column(name = "propriedade_id", nullable = false)
    private UUID propriedadeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoVinculo estado;

    @Column(name = "convidado_em", nullable = false)
    private Instant convidadoEm;

    @Column(name = "aceito_em")
    private Instant aceitoEm;

    protected VinculoAgronomoPropriedade() {}

    public VinculoAgronomoPropriedade(UUID agronomoId, UUID propriedadeId) {
        this.id = UUID.randomUUID();
        this.agronomoId = agronomoId;
        this.propriedadeId = propriedadeId;
        this.estado = EstadoVinculo.CONVIDADO;
        this.convidadoEm = Instant.now();
    }

    public void aceitar(Instant agora) {
        this.estado = EstadoVinculo.ACEITO;
        this.aceitoEm = agora;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getAgronomoId() {
        return agronomoId;
    }

    public UUID getPropriedadeId() {
        return propriedadeId;
    }

    public EstadoVinculo getEstado() {
        return estado;
    }

    public Instant getConvidadoEm() {
        return convidadoEm;
    }

    public Instant getAceitoEm() {
        return aceitoEm;
    }
}
