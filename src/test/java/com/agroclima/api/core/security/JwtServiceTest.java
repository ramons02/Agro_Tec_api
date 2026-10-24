package com.agroclima.api.core.security;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.core.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static JwtService servico(String segredo) {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt(segredo, "HS256", 24), null, null, null, null, null);
        return new JwtService(props, java.time.Clock.systemUTC());
    }

    @Test
    void segredoAusenteImpedeInicializacao() {
        assertThrows(IllegalStateException.class, () -> servico(null));
    }

    @Test
    void segredoCurtoImpedeInicializacao() {
        assertThrows(IllegalStateException.class, () -> servico("curto"));
    }

    @Test
    void segredoValidoAssinaEValidaToken() {
        JwtService jwt = servico("segredo-valido-com-mais-de-trinta-e-dois-bytes");
        UUID id = UUID.randomUUID();

        UsuarioAutenticado usuario = jwt.decodificar(jwt.criarToken(id, Papel.PRODUTOR_RURAL).token());

        assertEquals(id, usuario.id());
        assertEquals(Papel.PRODUTOR_RURAL, usuario.papel());
    }

    @Test
    void tokenAssinadoComOutroSegredoEhRecusado() {
        JwtService emissor = servico("segredo-valido-com-mais-de-trinta-e-dois-bytes");
        JwtService outro = servico("outro-segredo-valido-com-mais-de-trinta-bytes");
        String token = emissor.criarToken(UUID.randomUUID(), Papel.GESTOR_TECNOLOGIA).token();

        assertThrows(com.agroclima.api.core.response.AppException.class, () -> outro.decodificar(token));
    }

    @Test
    void tokenLevaAVersaoDeSessaoEOEscopo() {
        JwtService jwt = servico("segredo-valido-com-mais-de-trinta-e-dois-bytes");
        UUID id = UUID.randomUUID();

        JwtService.TokenDecodificado completo = jwt.decodificarCompleto(jwt.criarToken(id, Papel.AGRONOMO, 3).token());
        JwtService.TokenDecodificado restrito =
                jwt.decodificarCompleto(jwt.criarTokenRestrito(id, Papel.AGRONOMO, 4).token());

        assertEquals(3, completo.versaoSessao());
        assertFalse(completo.restrito());
        assertEquals(4, restrito.versaoSessao());
        assertTrue(restrito.restrito());
    }

    @Test
    void tokenRestritoValeTrintaMinutos() {
        JwtService jwt = servico("segredo-valido-com-mais-de-trinta-e-dois-bytes");

        JwtService.TokenGerado gerado = jwt.criarTokenRestrito(UUID.randomUUID(), Papel.PRODUTOR_RURAL, 0);

        long minutos = Duration.between(Instant.now(), gerado.expiraEm()).toMinutes();
        assertTrue(minutos >= 29 && minutos <= 30);
    }

    @Test
    void tokenSemClaimVerEmitidoAntesDaMigrationValeVersaoZero() {
        String segredo = "segredo-valido-com-mais-de-trinta-e-dois-bytes";
        JwtService jwt = servico(segredo);
        UUID id = UUID.randomUUID();
        String legado = io.jsonwebtoken.Jwts.builder()
                .subject(id.toString())
                .claim("papel", Papel.PRODUTOR_RURAL.name())
                .expiration(java.util.Date.from(Instant.now().plusSeconds(60)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        segredo.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        JwtService.TokenDecodificado decodificado = jwt.decodificarCompleto(legado);

        assertEquals(0, decodificado.versaoSessao());
        assertFalse(decodificado.restrito());
        assertEquals(id, decodificado.usuario().id());
    }

    @Test
    void relogioInjetadoDecideSeOTokenExpirou() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("segredo-valido-com-mais-de-trinta-e-dois-bytes", "HS256", 24),
                null, null, null, null, null);
        Instant inicio = Instant.parse("2026-10-24T12:00:00Z");
        JwtService emitido = new JwtService(props, Clock.fixed(inicio, ZoneOffset.UTC));
        String token = emitido.criarToken(UUID.randomUUID(), Papel.PRODUTOR_RURAL).token();

        JwtService quase = new JwtService(props, Clock.fixed(inicio.plus(Duration.ofHours(23)), ZoneOffset.UTC));
        JwtService depois = new JwtService(props, Clock.fixed(inicio.plus(Duration.ofHours(25)), ZoneOffset.UTC));

        assertEquals(Papel.PRODUTOR_RURAL, quase.decodificar(token).papel());
        assertThrows(com.agroclima.api.core.response.AppException.class, () -> depois.decodificar(token));
    }
}
