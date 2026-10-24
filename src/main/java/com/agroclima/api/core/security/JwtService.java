package com.agroclima.api.core.security;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.core.config.AppProperties;
import com.agroclima.api.core.response.AppException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT HS256, claims sub (uuid) + papel + ver (versao de sessao) + escopo opcional, expiracao
 * configuravel (24h por padrao) --
 * espelha criar_token/_decodificar_token de app/core/security.py. Mesma mensagem 401
 * generica em qualquer falha (ausente/invalido/expirado), nunca diferenciada.
 */
@Service
public class JwtService {

    public record TokenGerado(String token, Instant expiraEm) {}

    /** Token decodificado: o principal, a versao de sessao em que foi emitido e se tem escopo restrito. */
    public record TokenDecodificado(UsuarioAutenticado usuario, int versaoSessao, boolean restrito) {}

    static final String ESCOPO_TROCA_SENHA = "TROCA_SENHA";
    /** Token do primeiro acesso: so serve para POST /auth/trocar-senha, por isso vale pouco (spec 022, R3). */
    static final Duration VALIDADE_RESTRITO = Duration.ofMinutes(30);

    private final SecretKey chave;
    private final Duration validade;
    private final Clock clock;

    private static final int TAMANHO_MINIMO_SEGREDO_BYTES = 32;

    public JwtService(AppProperties appProperties, Clock clock) {
        this.clock = clock;
        String segredo = appProperties.jwt().secret();
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_SEGREDO_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET ausente ou com menos de " + TAMANHO_MINIMO_SEGREDO_BYTES
                            + " bytes. Gere um com `openssl rand -hex 32`.");
        }
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.validade = Duration.ofHours(appProperties.jwt().expirationHours());
    }

    public TokenGerado criarToken(UUID usuarioId, Papel papel) {
        return criarToken(usuarioId, papel, 0);
    }

    public TokenGerado criarToken(UUID usuarioId, Papel papel, int versaoSessao) {
        return construir(usuarioId, papel, versaoSessao, validade, null);
    }

    public TokenGerado criarTokenRestrito(UUID usuarioId, Papel papel, int versaoSessao) {
        return construir(usuarioId, papel, versaoSessao, VALIDADE_RESTRITO, ESCOPO_TROCA_SENHA);
    }

    private TokenGerado construir(UUID usuarioId, Papel papel, int versaoSessao, Duration duracao, String escopo) {
        Instant agora = clock.instant();
        Instant expiraEm = agora.plus(duracao);
        var builder = Jwts.builder()
                .subject(usuarioId.toString())
                .claim("papel", papel.name())
                .claim("ver", versaoSessao)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiraEm));
        if (escopo != null) {
            builder.claim("escopo", escopo);
        }
        return new TokenGerado(builder.signWith(chave).compact(), expiraEm);
    }

    public UsuarioAutenticado decodificar(String token) {
        return decodificarCompleto(token).usuario();
    }

    public TokenDecodificado decodificarCompleto(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chave)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID id = UUID.fromString(claims.getSubject());
            Papel papel = Papel.valueOf(claims.get("papel", String.class));
            // Token emitido antes da migration V16 nao tem o claim ver: vale como versao 0.
            Integer versao = claims.get("ver", Integer.class);
            boolean restrito = ESCOPO_TROCA_SENHA.equals(claims.get("escopo", String.class));
            return new TokenDecodificado(new UsuarioAutenticado(id, papel), versao == null ? 0 : versao, restrito);
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            throw new AppException(401, "Token de autorização ausente ou expirado.");
        }
    }
}
