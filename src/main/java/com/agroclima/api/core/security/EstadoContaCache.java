package com.agroclima.api.core.security;

import com.agroclima.api.business.auth.UsuarioRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache de 5 s do estado da conta, para o filtro JWT nao ir ao banco em toda requisicao. A API roda
 * numa instancia so, entao cada acao de gestao chama {@link #invalidar} e o efeito e imediato; os 5 s
 * sao so a rede de seguranca (SC-003 pede ate 10 s).
 */
@Component
public class EstadoContaCache {

    private static final Duration VALIDADE = Duration.ofSeconds(5);

    private final UsuarioRepository usuarioRepository;
    private final Cache<UUID, Optional<EstadoConta>> cache =
            Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(VALIDADE).build();

    public EstadoContaCache(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /** Vazio quando a conta nao existe mais. */
    public Optional<EstadoConta> obter(UUID usuarioId) {
        return cache.get(usuarioId, id -> usuarioRepository.findById(id)
                .map(u -> new EstadoConta(u.getStatus(), u.getVersaoSessao(), u.isPrecisaTrocarSenha())));
    }

    public void invalidar(UUID usuarioId) {
        cache.invalidate(usuarioId);
    }
}
