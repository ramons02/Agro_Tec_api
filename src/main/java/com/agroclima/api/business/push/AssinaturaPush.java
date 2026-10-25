package com.agroclima.api.business.push;

import com.agroclima.api.core.base.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Um navegador/aparelho que aceitou receber notificação (Web Push). O endpoint identifica o aparelho. */
@Entity
@Table(name = "assinaturas_push")
public class AssinaturaPush extends BaseModel<UUID> {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, unique = true)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected AssinaturaPush() {}

    public AssinaturaPush(UUID usuarioId, String endpoint, String p256dh, String auth, Instant agora) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.criadoEm = agora;
    }

    /** O mesmo aparelho entrou com outra conta: a assinatura passa a ser dela. */
    public void reatribuir(UUID novoUsuarioId, String p256dh, String auth) {
        this.usuarioId = novoUsuarioId;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }
}
