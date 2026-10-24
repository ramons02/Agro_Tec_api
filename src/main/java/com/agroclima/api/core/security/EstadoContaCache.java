package com.agroclima.api.core.security;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.grupo.ParticipacaoAgronomo;
import com.agroclima.api.business.grupo.ParticipacaoAgronomoRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
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
    private final ParticipacaoAgronomoRepository participacaoRepository;
    private final Cache<UUID, Optional<EstadoConta>> cache =
            Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(VALIDADE).build();

    public EstadoContaCache(
            UsuarioRepository usuarioRepository, ParticipacaoAgronomoRepository participacaoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.participacaoRepository = participacaoRepository;
    }

    /** Vazio quando a conta nao existe mais. */
    public Optional<EstadoConta> obter(UUID usuarioId) {
        return cache.get(usuarioId, id -> usuarioRepository.findById(id).map(u -> {
            List<UUID> grupos = u.getPapel() == Papel.AGRONOMO
                    ? participacaoRepository.findByIdAgronomoId(id).stream()
                            .map(ParticipacaoAgronomo::getGrupoId)
                            .toList()
                    : List.of();
            return new EstadoConta(
                    u.getStatus(), u.getVersaoSessao(), u.isPrecisaTrocarSenha(), u.getPapel(), u.getGrupoId(), grupos);
        }));
    }

    public void invalidar(UUID usuarioId) {
        cache.invalidate(usuarioId);
    }
}
