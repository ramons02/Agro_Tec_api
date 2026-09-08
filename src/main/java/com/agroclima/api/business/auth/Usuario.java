package com.agroclima.api.business.auth;

import com.agroclima.api.core.base.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
public class Usuario extends BaseModel<UUID> {

    @Id
    private UUID id;

    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "papel_usuario")
    private Papel papel;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Usuario() {}

    public Usuario(String nome, String email, String senhaHash, Papel papel) {
        this.id = UUID.randomUUID();
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.papel = papel;
        this.criadoEm = Instant.now();
    }

    public void atualizarSenha(String novaSenhaHash) {
        this.senhaHash = novaSenhaHash;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Papel getPapel() {
        return papel;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
