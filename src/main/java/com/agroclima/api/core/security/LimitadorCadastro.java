package com.agroclima.api.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Limite de cadastros por hora, por origem (IP do cliente), do cadastro publico (spec 022, FR-003/R5).
 * Em memoria, como o {@link LimitadorTentativas}: a API roda numa instancia so. O pior caso de um
 * limite contornado e uma fila de pendentes maior, ninguem ganha acesso sem aprovacao do gestor.
 */
@Component
public class LimitadorCadastro {

    private final int maximoPorHora;
    private LimitadorTentativas limitador;

    public LimitadorCadastro(@Value("${app.cadastro.limite-por-hora:3}") int maximoPorHora) {
        this.maximoPorHora = maximoPorHora;
        this.limitador = novo();
    }

    /** Conta uma tentativa da origem e diz se ainda cabe no limite. */
    public boolean consumir(String origem) {
        return limitador.consumir(origem);
    }

    /** Zera os contadores (usado pelos testes, que cadastram varias vezes pelo mesmo IP). */
    public synchronized void reiniciar() {
        this.limitador = novo();
    }

    private LimitadorTentativas novo() {
        return new LimitadorTentativas(maximoPorHora, Duration.ofHours(1));
    }
}
