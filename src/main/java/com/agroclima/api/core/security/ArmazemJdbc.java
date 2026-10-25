package com.agroclima.api.core.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Contagens no banco (tabela {@code limite_tentativas}). Cada operacao roda em transacao propria
 * (REQUIRES_NEW): o login falho lanca excecao e desfaz a transacao dele, e a falha contada nao pode
 * desaparecer junto. A chave (e-mail ou IP) vai so como hash SHA-256.
 */
@Component
public class ArmazemJdbc implements ArmazemDeContagem {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;

    public ArmazemJdbc(JdbcTemplate jdbc, PlatformTransactionManager gerenteDeTransacao) {
        this.jdbc = jdbc;
        this.transacao = new TransactionTemplate(gerenteDeTransacao);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public int atual(String escopo, String chave) {
        Integer contagem = transacao.execute(status -> jdbc.query(
                "SELECT contagem FROM limite_tentativas WHERE escopo = ? AND chave_hash = ? AND expira_em > now()",
                resultado -> resultado.next() ? resultado.getInt(1) : 0,
                escopo, hash(chave)));
        return contagem == null ? 0 : contagem;
    }

    @Override
    public int incrementar(String escopo, String chave, Duration janela) {
        // Uma instrucao so, atomica: linha vencida recomeca em 1; linha vigente soma 1. Toda tentativa renova o prazo.
        Integer contagem = transacao.execute(status -> jdbc.queryForObject(
                "INSERT INTO limite_tentativas (escopo, chave_hash, contagem, expira_em) "
                        + "VALUES (?, ?, 1, now() + (? * interval '1 second')) "
                        + "ON CONFLICT (escopo, chave_hash) DO UPDATE SET "
                        + "contagem = CASE WHEN limite_tentativas.expira_em > now() THEN limite_tentativas.contagem + 1 ELSE 1 END, "
                        + "expira_em = now() + (? * interval '1 second') "
                        + "RETURNING contagem",
                Integer.class, escopo, hash(chave), janela.toSeconds(), janela.toSeconds()));
        return contagem == null ? 0 : contagem;
    }

    @Override
    public void remover(String escopo, String chave) {
        transacao.executeWithoutResult(status -> jdbc.update(
                "DELETE FROM limite_tentativas WHERE escopo = ? AND chave_hash = ?", escopo, hash(chave)));
    }

    @Override
    public void removerEscopo(String escopo) {
        transacao.executeWithoutResult(status -> jdbc.update("DELETE FROM limite_tentativas WHERE escopo = ?", escopo));
    }

    /** Apaga as linhas vencidas (job diario); a tabela nao cresce sem limite. */
    public int removerVencidos() {
        Integer apagadas = transacao.execute(status -> jdbc.update("DELETE FROM limite_tentativas WHERE expira_em < now()"));
        return apagadas == null ? 0 : apagadas;
    }

    static String hash(String chave) {
        String normalizada = chave == null ? "" : chave.trim().toLowerCase(Locale.ROOT);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalizada.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
