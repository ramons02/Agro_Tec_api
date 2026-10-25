package com.agroclima.api.core.security;

import java.time.Duration;

/** Onde o {@link LimitadorTentativas} guarda as contagens: em memoria (testes) ou no banco (producao). */
public interface ArmazemDeContagem {

    /** Contagem vigente da chave no escopo, ou 0 se nao ha ou se a janela ja venceu. */
    int atual(String escopo, String chave);

    /** Soma uma tentativa, renova a janela (deslizante) e devolve a nova contagem. */
    int incrementar(String escopo, String chave, Duration janela);

    void remover(String escopo, String chave);

    void removerEscopo(String escopo);
}
