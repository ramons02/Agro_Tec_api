package com.agroclima.api.business.auth;

import com.agroclima.api.core.base.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Convite de uso unico, amarrado a um e-mail, para criar conta GESTOR_TECNOLOGIA. */
@Entity
@Table(name = "convites_gestor")
public class ConviteGestor extends BaseModel<UUID> {

    public static final Duration VALIDADE = Duration.ofDays(7);

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private String email;

    @Column(name = "criado_por", nullable = false)
    private UUID criadoPor;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    protected ConviteGestor() {}

    public ConviteGestor(String tokenHash, String email, UUID criadoPor) {
        this.id = UUID.randomUUID();
        this.tokenHash = tokenHash;
        this.email = email;
        this.criadoPor = criadoPor;
        this.criadoEm = Instant.now();
        this.expiraEm = this.criadoEm.plus(VALIDADE);
    }

    public boolean estaValidoPara(String emailDoCadastro, Instant agora) {
        return usadoEm == null && expiraEm.isAfter(agora) && email.equalsIgnoreCase(emailDoCadastro.trim());
    }

    public void marcarUsado(Instant agora) {
        this.usadoEm = agora;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getUsadoEm() {
        return usadoEm;
    }
}
