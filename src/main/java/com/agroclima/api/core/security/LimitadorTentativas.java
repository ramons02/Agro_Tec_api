package com.agroclima.api.core.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Locale;

/**
 * Limite de tentativas por chave (e-mail), em memoria -- a API roda numa instancia so (Render free),
 * entao nao precisa de Redis. A janela e deslizante: cada nova tentativa renova o prazo da chave,
 * entao o bloqueio dura {@code janela} a partir da ultima tentativa. Chave por e-mail e nao por IP
 * de proposito: atras do proxy do Render o IP real nao e confiavel.
 */
public class LimitadorTentativas {

    private final int maximo;
    private final Cache<String, Integer> contadores;

    public LimitadorTentativas(int maximo, Duration janela) {
        this.maximo = maximo;
        this.contadores = Caffeine.newBuilder().maximumSize(50_000).expireAfterWrite(janela).build();
    }

    /** Ja atingiu o maximo de tentativas dentro da janela? Nao altera o contador. */
    public boolean bloqueado(String chave) {
        Integer usadas = contadores.getIfPresent(normalizar(chave));
        return usadas != null && usadas >= maximo;
    }

    public void registrarFalha(String chave) {
        contadores.asMap().merge(normalizar(chave), 1, Integer::sum);
    }

    public void limpar(String chave) {
        contadores.invalidate(normalizar(chave));
    }

    /** Conta uma tentativa e diz se ela ainda cabe no limite. */
    public boolean consumir(String chave) {
        return contadores.asMap().merge(normalizar(chave), 1, Integer::sum) <= maximo;
    }

    private static String normalizar(String chave) {
        return chave == null ? "" : chave.trim().toLowerCase(Locale.ROOT);
    }
}
