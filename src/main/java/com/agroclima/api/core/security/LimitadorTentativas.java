package com.agroclima.api.core.security;

import java.time.Duration;

/**
 * Limite de tentativas por chave (e-mail ou IP) dentro de um escopo (login, recuperacao, cadastro...).
 * A janela e deslizante: cada nova tentativa renova o prazo da chave, entao o bloqueio dura {@code janela}
 * a partir da ultima tentativa. As contagens ficam num {@link ArmazemDeContagem}: no banco em producao,
 * para sobreviver a reinicio e valer com mais de uma instancia. Chave por e-mail e nao por IP no login,
 * de proposito: o IP real atras do proxy do Render nao e confiavel.
 */
public class LimitadorTentativas {

    private final String escopo;
    private final int maximo;
    private final Duration janela;
    private final ArmazemDeContagem armazem;

    public LimitadorTentativas(String escopo, int maximo, Duration janela, ArmazemDeContagem armazem) {
        this.escopo = escopo;
        this.maximo = maximo;
        this.janela = janela;
        this.armazem = armazem;
    }

    /** Ja atingiu o maximo de tentativas dentro da janela? Nao altera o contador. */
    public boolean bloqueado(String chave) {
        return armazem.atual(escopo, chave) >= maximo;
    }

    public void registrarFalha(String chave) {
        armazem.incrementar(escopo, chave, janela);
    }

    public void limpar(String chave) {
        armazem.remover(escopo, chave);
    }

    /** Conta uma tentativa e diz se ela ainda cabe no limite. */
    public boolean consumir(String chave) {
        return armazem.incrementar(escopo, chave, janela) <= maximo;
    }
}
