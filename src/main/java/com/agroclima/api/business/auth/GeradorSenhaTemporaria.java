package com.agroclima.api.business.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Senha temporaria (spec 022, R4): 14 caracteres de um alfabeto sem ambiguos (sem 0 O 1 l I), com
 * ao menos uma letra e um numero. O texto claro so existe na resposta da acao que a gerou.
 */
@Component
public class GeradorSenhaTemporaria {

    static final int TAMANHO = 14;
    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final String NUMEROS = "23456789";
    private static final String ALFABETO = LETRAS + NUMEROS;

    private final SecureRandom random = new SecureRandom();

    public String gerar() {
        char[] senha = new char[TAMANHO];
        senha[0] = sortear(LETRAS);
        senha[1] = sortear(NUMEROS);
        for (int i = 2; i < TAMANHO; i++) {
            senha[i] = sortear(ALFABETO);
        }
        // Embaralha (Fisher-Yates) para a letra e o numero garantidos nao ficarem sempre no inicio.
        for (int i = TAMANHO - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = senha[i];
            senha[i] = senha[j];
            senha[j] = tmp;
        }
        return new String(senha);
    }

    private char sortear(String origem) {
        return origem.charAt(random.nextInt(origem.length()));
    }
}
