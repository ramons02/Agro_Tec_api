package com.agroclima.api.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Limite de cadastros por hora, por origem (IP do cliente), do cadastro publico (spec 022, FR-003/R5).
 * Guardado no banco, como o {@link LimitadorTentativas}. O pior caso de um limite contornado e uma fila
 * de pendentes maior: ninguem ganha acesso sem aprovacao do gestor.
 */
@Component
public class LimitadorCadastro {

    private static final String ESCOPO = "cadastro";

    private final LimitadorTentativas limitador;
    private final ArmazemDeContagem armazem;

    public LimitadorCadastro(@Value("${app.cadastro.limite-por-hora:3}") int maximoPorHora, ArmazemJdbc armazem) {
        this.armazem = armazem;
        this.limitador = new LimitadorTentativas(ESCOPO, maximoPorHora, Duration.ofHours(1), armazem);
    }

    /** Conta uma tentativa da origem e diz se ainda cabe no limite. */
    public boolean consumir(String origem) {
        return limitador.consumir(origem);
    }

    /** Zera os contadores (usado pelos testes, que cadastram varias vezes pelo mesmo IP). */
    public void reiniciar() {
        armazem.removerEscopo(ESCOPO);
    }
}
