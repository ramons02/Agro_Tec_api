package com.agroclima.api.business.auth;

import com.agroclima.api.core.config.AppProperties;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceRateLimitTest {

    private UsuarioRepository usuarios;
    private TokenRecuperacaoSenhaRepository tokens;
    private EmailService email;
    private AuthService service;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        tokens = mock(TokenRecuperacaoSenhaRepository.class);
        email = mock(EmailService.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        JwtService jwt = mock(JwtService.class);
        when(jwt.criarToken(any(), any())).thenReturn(new JwtService.TokenGerado("t", Instant.now()));
        AppProperties props = new AppProperties(null, null, "https://app.exemplo", null, null, null, null, null);
        service = new AuthService(usuarios, tokens, mock(ConviteGestorRepository.class), encoder, jwt, email, props);
        usuario = new Usuario("Fulano", "fulano@x.com", encoder.encode("senha-correta"), Papel.PRODUTOR_RURAL);
        when(usuarios.findByEmail("fulano@x.com")).thenReturn(Optional.of(usuario));
    }

    private void falharLogin(String emailUsado) {
        assertEquals(401, assertThrows(AppException.class, () -> service.login(emailUsado, "errada")).getCodigo());
    }

    @Test
    void sextaTentativaErradaVira429MesmoComSenhaCerta() {
        for (int i = 0; i < 5; i++) {
            falharLogin("fulano@x.com");
        }

        assertEquals(429, assertThrows(AppException.class,
                () -> service.login("fulano@x.com", "senha-correta")).getCodigo());
    }

    @Test
    void emailInexistenteTambemEhLimitadoSemRevelarExistencia() {
        when(usuarios.findByEmail(anyString())).thenReturn(Optional.empty());
        for (int i = 0; i < 5; i++) {
            falharLogin("fantasma@x.com");
        }

        assertEquals(429, assertThrows(AppException.class, () -> service.login("fantasma@x.com", "x")).getCodigo());
    }

    @Test
    void bloqueioDeUmEmailNaoAfetaOutro() {
        for (int i = 0; i < 5; i++) {
            falharLogin("fulano@x.com");
        }
        Usuario outro = new Usuario("Outro", "outro@x.com", new BCryptPasswordEncoder().encode("ok-ok-ok-ok"),
                Papel.AGRONOMO);
        when(usuarios.findByEmail("outro@x.com")).thenReturn(Optional.of(outro));

        assertEquals(Papel.AGRONOMO, service.login("outro@x.com", "ok-ok-ok-ok").papel());
    }

    @Test
    void loginCertoZeraOContador() {
        for (int i = 0; i < 4; i++) {
            falharLogin("fulano@x.com");
        }
        service.login("fulano@x.com", "senha-correta");
        for (int i = 0; i < 4; i++) {
            falharLogin("fulano@x.com");
        }

        assertEquals(Papel.PRODUTOR_RURAL, service.login("fulano@x.com", "senha-correta").papel());
    }

    @Test
    void emailComCaixaDiferenteContaNoMesmoLimite() {
        when(usuarios.findByEmail("Fulano@X.com")).thenReturn(Optional.of(usuario));
        for (int i = 0; i < 5; i++) {
            falharLogin(i % 2 == 0 ? "fulano@x.com" : "Fulano@X.com");
        }

        assertEquals(429, assertThrows(AppException.class,
                () -> service.login("FULANO@x.com", "senha-correta")).getCodigo());
    }

    @Test
    void quartoPedidoDeRecuperacaoNaoEnviaMaisEmailMasNaoFalha() {
        when(usuarios.findByEmail("fulano@x.com")).thenReturn(Optional.of(usuario));
        for (int i = 0; i < 6; i++) {
            service.solicitarRecuperacaoSenha("fulano@x.com");
        }

        verify(email, times(3)).enviarRecuperacaoSenha(anyString(), anyString());
        verify(tokens, times(3)).save(any());
    }

    @Test
    void recuperacaoDeEmailInexistenteNaoEnviaNada() {
        when(usuarios.findByEmail(anyString())).thenReturn(Optional.empty());

        service.solicitarRecuperacaoSenha("ninguem@x.com");

        verify(email, never()).enviarRecuperacaoSenha(anyString(), anyString());
    }
}
