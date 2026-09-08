package com.agroclima.api.business.auth;

import com.agroclima.api.core.base.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** FR-004 -- token opaco de recuperacao de senha, validade de 1h, uso unico. */
@Entity
@Table(name = "tokens_recuperacao_senha")
public class TokenRecuperacaoSenha extends BaseModel<UUID> {

    public static final Duration VALIDADE = Duration.ofHours(1);

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    protected TokenRecuperacaoSenha() {}

    public TokenRecuperacaoSenha(UUID usuarioId, String token) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.token = token;
        this.criadoEm = Instant.now();
        this.expiraEm = this.criadoEm.plus(VALIDADE);
    }

    public boolean estaValido(Instant agora) {
        return usadoEm == null && expiraEm.isAfter(agora);
    }

    public void marcarUsado(Instant agora) {
        this.usadoEm = agora;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getToken() {
        return token;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getUsadoEm() {
        return usadoEm;
    }
}
