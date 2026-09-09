package com.agroclima.api.business.telegram;

import com.agroclima.api.core.calculos.ClassificacaoPulverizacao;
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

/** Um registro por talhao -- guarda a ultima classificacao conhecida pra detectar a
 * transicao bloqueada->FAVORAVEL sem repetir o alerta a cada ciclo do job (feature 017,
 * data-model.md, research.md). PK e o proprio talhaoId (nao um UUID novo), entao nao
 * extende BaseModel&lt;UUID&gt; -- e uma entidade de estado, nao um recurso de dominio. */
@Entity
@Table(name = "estado_alerta_pulverizacao")
public class EstadoAlertaPulverizacao {

    @Id
    @Column(name = "talhao_id")
    private UUID talhaoId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "ultima_classificacao", nullable = false, columnDefinition = "classificacao_pulverizacao")
    private ClassificacaoPulverizacao ultimaClassificacao;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected EstadoAlertaPulverizacao() {}

    public EstadoAlertaPulverizacao(UUID talhaoId, ClassificacaoPulverizacao ultimaClassificacao, Instant agora) {
        this.talhaoId = talhaoId;
        this.ultimaClassificacao = ultimaClassificacao;
        this.atualizadoEm = agora;
    }

    public void atualizar(ClassificacaoPulverizacao novaClassificacao, Instant agora) {
        this.ultimaClassificacao = novaClassificacao;
        this.atualizadoEm = agora;
    }

    public UUID getTalhaoId() {
        return talhaoId;
    }

    public ClassificacaoPulverizacao getUltimaClassificacao() {
        return ultimaClassificacao;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
