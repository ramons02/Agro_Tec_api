package com.agroclima.api.business.grupo;

import com.agroclima.api.core.base.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Grupo (tenant): o conjunto de um Admin e das contas e dados dele (spec 024). So o hash do codigo do link de
 * cadastro e guardado; o codigo em claro aparece uma unica vez, na geracao.
 */
@Entity
@Table(name = "grupos")
public class Grupo extends BaseModel<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "codigo_cadastro_hash", nullable = false)
    private String codigoCadastroHash;

    @Column(name = "codigo_cadastro_gerado_em", nullable = false)
    private Instant codigoCadastroGeradoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Grupo() {}

    public Grupo(String nome, UUID adminId, String codigoCadastroHash, Instant agora) {
        this.id = UUID.randomUUID();
        this.nome = nome;
        this.adminId = adminId;
        this.codigoCadastroHash = codigoCadastroHash;
        this.codigoCadastroGeradoEm = agora;
        this.criadoEm = agora;
    }

    /** O codigo antigo deixa de valer no mesmo instante: so o hash do atual e guardado. */
    public void renovarCodigoCadastro(String novoHash, Instant agora) {
        this.codigoCadastroHash = novoHash;
        this.codigoCadastroGeradoEm = agora;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public String getCodigoCadastroHash() {
        return codigoCadastroHash;
    }

    public Instant getCodigoCadastroGeradoEm() {
        return codigoCadastroGeradoEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
