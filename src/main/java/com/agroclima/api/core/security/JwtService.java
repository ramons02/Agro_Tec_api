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
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT HS256, claims sub (uuid) + papel, expiracao configuravel (24h por padrao) --
 * espelha criar_token/_decodificar_token de app/core/security.py. Mesma mensagem 401
 * generica em qualquer falha (ausente/invalido/expirado), nunca diferenciada.
 */
@Service
public class JwtService {

    public record TokenGerado(String token, Instant expiraEm) {}

    private final SecretKey chave;
    private final Duration validade;

    public JwtService(AppProperties appProperties) {
        this.chave = Keys.hmacShaKeyFor(appProperties.jwt().secret().getBytes());
        this.validade = Duration.ofHours(appProperties.jwt().expirationHours());
    }

    public TokenGerado criarToken(UUID usuarioId, Papel papel) {
        Instant agora = Instant.now();
        Instant expiraEm = agora.plus(validade);
        String token = Jwts.builder()
                .subject(usuarioId.toString())
                .claim("papel", papel.name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiraEm))
                .signWith(chave)
                .compact();
        return new TokenGerado(token, expiraEm);
    }

    public UsuarioAutenticado decodificar(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(chave).build().parseSignedClaims(token).getPayload();
            UUID id = UUID.fromString(claims.getSubject());
            Papel papel = Papel.valueOf(claims.get("papel", String.class));
            return new UsuarioAutenticado(id, papel);
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            throw new AppException(401, "Token de autorização ausente ou expirado.");
        }
    }
}
