package com.agroclima.api.business.alerta;

import com.agroclima.api.core.base.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Aviso que a pessoa vê no sino do app (janela de pulverização aberta, rajada forte). Um por pessoa. */
@Entity
@Table(name = "alertas")
public class Alerta extends BaseModel<UUID> {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "talhao_id", nullable = false)
    private UUID talhaoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoAlerta tipo;

    @Column(nullable = false)
    private String mensagem;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "lido_em")
    private Instant lidoEm;

    protected Alerta() {}

    public Alerta(UUID usuarioId, UUID talhaoId, TipoAlerta tipo, String mensagem, Instant agora) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.talhaoId = talhaoId;
        this.tipo = tipo;
        this.mensagem = mensagem;
        this.criadoEm = agora;
    }

    public void marcarLido(Instant agora) {
        if (lidoEm == null) {
            lidoEm = agora;
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getTalhaoId() {
        return talhaoId;
    }

    public TipoAlerta getTipo() {
        return tipo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getLidoEm() {
        return lidoEm;
    }
}
