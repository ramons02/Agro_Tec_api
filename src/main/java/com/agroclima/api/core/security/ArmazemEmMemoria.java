package com.agroclima.api.core.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Armazem em memoria: serve aos testes unitarios. Em producao o limite vai para o banco ({@link ArmazemJdbc}). */
public class ArmazemEmMemoria implements ArmazemDeContagem {

    private final ConcurrentMap<String, Cache<String, Integer>> porEscopoEJanela = new ConcurrentHashMap<>();

    private Cache<String, Integer> cache(String escopo, Duration janela) {
        return porEscopoEJanela.computeIfAbsent(escopo + "|" + janela,
                k -> Caffeine.newBuilder().maximumSize(50_000).expireAfterWrite(janela).build());
    }

    private static String normalizar(String chave) {
        return chave == null ? "" : chave.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public int atual(String escopo, String chave) {
        int maior = 0;
        for (var entrada : porEscopoEJanela.entrySet()) {
            if (entrada.getKey().startsWith(escopo + "|")) {
                Integer valor = entrada.getValue().getIfPresent(normalizar(chave));
                maior = Math.max(maior, valor == null ? 0 : valor);
            }
        }
        return maior;
    }

    @Override
    public int incrementar(String escopo, String chave, Duration janela) {
        return cache(escopo, janela).asMap().merge(normalizar(chave), 1, Integer::sum);
    }

    @Override
    public void remover(String escopo, String chave) {
        porEscopoEJanela.forEach((k, v) -> {
            if (k.startsWith(escopo + "|")) {
                v.invalidate(normalizar(chave));
            }
        });
    }

    @Override
    public void removerEscopo(String escopo) {
        porEscopoEJanela.forEach((k, v) -> {
            if (k.startsWith(escopo + "|")) {
                v.invalidateAll();
            }
        });
    }
}
