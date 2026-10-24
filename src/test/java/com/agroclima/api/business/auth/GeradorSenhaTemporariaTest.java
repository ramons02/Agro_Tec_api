package com.agroclima.api.business.auth;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeradorSenhaTemporariaTest {

    private final GeradorSenhaTemporaria gerador = new GeradorSenhaTemporaria();

    @Test
    void temTamanhoMinimoDe12ECaracteresSemAmbiguos() {
        for (int i = 0; i < 1_000; i++) {
            String senha = gerador.gerar();

            assertTrue(senha.length() >= 12);
            assertEquals(14, senha.length());
            assertFalse(senha.matches(".*[0O1lI].*"), "caractere ambiguo em " + senha);
        }
    }

    @Test
    void sempreTemUmaLetraEUmNumero() {
        for (int i = 0; i < 1_000; i++) {
            String senha = gerador.gerar();

            assertTrue(senha.matches(".*[A-Za-z].*"), "sem letra em " + senha);
            assertTrue(senha.matches(".*[0-9].*"), "sem numero em " + senha);
        }
    }

    @Test
    void milSenhasNaoSeRepetem() {
        Set<String> geradas = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            geradas.add(gerador.gerar());
        }

        assertEquals(1_000, geradas.size());
    }
}
