package com.agroclima.api.business.usuario;

import com.agroclima.api.core.base.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Um item da trilha de auditoria. alvoId e nulo quando a conta deixou de existir (recusa); o e-mail
 * do alvo fica guardado para o registro continuar consultavel. Nunca guarda senha.
 */
@Entity
@Table(name = "auditoria_contas")
public class AuditoriaConta extends BaseModel<UUID> {

    @Id
    private UUID id;

    @Column(name = "ator_id", nullable = false)
    private UUID atorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AcaoAuditoria acao;

    @Column(name = "alvo_id")
    private UUID alvoId;

    @Column(name = "alvo_email", nullable = false)
    private String alvoEmail;

    private String detalhe;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected AuditoriaConta() {}

    public AuditoriaConta(
            UUID atorId, AcaoAuditoria acao, UUID alvoId, String alvoEmail, String detalhe, Instant criadoEm) {
        this.id = UUID.randomUUID();
        this.atorId = atorId;
        this.acao = acao;
        this.alvoId = alvoId;
        this.alvoEmail = alvoEmail;
        this.detalhe = detalhe;
        this.criadoEm = criadoEm;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getAtorId() {
        return atorId;
    }

    public AcaoAuditoria getAcao() {
        return acao;
    }

    public UUID getAlvoId() {
        return alvoId;
    }

    public String getAlvoEmail() {
        return alvoEmail;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
