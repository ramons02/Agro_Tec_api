package com.agroclima.api.business.propriedade;

import com.agroclima.api.core.base.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.MultiPolygon;

import java.util.UUID;

@Entity
@Table(name = "propriedades")
public class Propriedade extends BaseModel<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nome;

    private String municipio;

    @Column(name = "proprietario_id", nullable = false)
    private UUID proprietarioId;

    private MultiPolygon geometria;

    protected Propriedade() {}

    public Propriedade(String nome, String municipio, UUID proprietarioId, MultiPolygon geometria) {
        this.id = UUID.randomUUID();
        this.nome = nome;
        this.municipio = municipio;
        this.proprietarioId = proprietarioId;
        this.geometria = geometria;
    }

    public void atualizar(String nome, String municipio, MultiPolygon geometria) {
        this.nome = nome;
        this.municipio = municipio;
        this.geometria = geometria;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getMunicipio() {
        return municipio;
    }

    public UUID getProprietarioId() {
        return proprietarioId;
    }

    public MultiPolygon getGeometria() {
        return geometria;
    }
}
