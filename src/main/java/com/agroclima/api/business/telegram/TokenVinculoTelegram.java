package com.agroclima.api.business.telegram;

import com.agroclima.api.core.base.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Token opaco de uso unico pro deep link `/start <token>` (feature 017, data-model.md). Mesmo
 * padrao de TokenRecuperacaoSenha -- validade mais generosa (24h, nao 1h) porque o fluxo exige
 * abrir o Telegram e mandar uma mensagem, nao so clicar um link de email. */
@Entity
@Table(name = "tokens_vinculo_telegram")
public class TokenVinculoTelegram extends BaseModel<UUID> {

    public static final Duration VALIDADE = Duration.ofHours(24);

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

    protected TokenVinculoTelegram() {}

    public TokenVinculoTelegram(UUID usuarioId, String token) {
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
}
