package com.agroclima.api.business.auth;

import com.agroclima.api.core.config.AppProperties;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.security.EstadoContaCache;
import com.agroclima.api.core.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras de papel, senha e e-mail do cadastro (spec 022, FR-001, FR-003, FR-004). */
class AuthServiceRegistroTest {

    private static final Instant AGORA = Instant.parse("2026-10-24T12:00:00Z");

    private UsuarioRepository usuarios;
    private AuthService service;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        AppProperties props = new AppProperties(null, null, "https://app.exemplo", null, null, null);
        service = new AuthService(usuarios, mock(TokenRecuperacaoSenhaRepository.class),
                mock(ConviteGestorRepository.class), new BCryptPasswordEncoder(), mock(JwtService.class),
                mock(EmailService.class), props, mock(EstadoContaCache.class), Clock.fixed(AGORA, ZoneOffset.UTC),
                mock(com.agroclima.api.business.usuario.AuditoriaContaRepository.class),
                mock(com.agroclima.api.business.grupo.ParticipacaoAgronomoRepository.class));
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void produtorSemSenhaNasceCriadoPendenteSemSenhaNaDataDoRelogio() {
        Usuario criado = service.registrar("N", "p@x.com", null, Papel.PRODUTOR_RURAL, null);

        assertEquals(StatusConta.PENDENTE, criado.getStatus());
        assertNull(criado.getSenhaHash());
        assertEquals(AGORA, criado.getCriadoEm());
    }

    @Test
    void produtorComSenhaERecusado() {
        AppException ex = assertThrows(AppException.class,
                () -> service.registrar("N", "p@x.com", "senha12345", Papel.PRODUTOR_RURAL, null));

        assertEquals(400, ex.getCodigo());
        verify(usuarios, never()).save(any());
    }

    @Test
    void emailExistenteEmQualquerCaixaDa409() {
        when(usuarios.existsByEmailIgnoreCase("p@x.com")).thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> service.registrar("N", "p@x.com", null, Papel.AGRONOMO, null));

        assertEquals(409, ex.getCodigo());
    }

    @Test
    void gestorSemConviteERecusadoAntesDeQualquerGravacao() {
        AppException ex = assertThrows(AppException.class,
                () -> service.registrar("N", "g@x.com", "senha12345", Papel.GESTOR_TECNOLOGIA, null));

        assertEquals(403, ex.getCodigo());
        verify(usuarios, never()).save(any());
    }
}
