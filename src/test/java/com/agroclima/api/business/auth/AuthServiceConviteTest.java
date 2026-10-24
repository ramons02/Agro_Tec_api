package com.agroclima.api.business.auth;

import com.agroclima.api.core.config.AppProperties;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.security.JwtService;
import com.agroclima.api.core.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceConviteTest {

    private UsuarioRepository usuarios;
    private ConviteGestorRepository convites;
    private AuthService service;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        convites = mock(ConviteGestorRepository.class);
        AppProperties props = new AppProperties(null, null, "https://app.exemplo", null, null, null);
        service = new AuthService(usuarios, mock(TokenRecuperacaoSenhaRepository.class), convites,
                new BCryptPasswordEncoder(), mock(JwtService.class), mock(EmailService.class), props);
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
    }

    private String criarConviteEDevolverToken(String email) {
        UsuarioAutenticado gestor = new UsuarioAutenticado(UUID.randomUUID(), Papel.GESTOR_TECNOLOGIA);
        AuthService.ConviteCriado criado = service.criarConviteGestor(gestor, email);
        return criado.link().substring(criado.link().indexOf("convite=") + 8);
    }

    private ConviteGestor conviteSalvo() {
        ArgumentCaptor<ConviteGestor> captor = ArgumentCaptor.forClass(ConviteGestor.class);
        verify(convites).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void registroDeGestorSemConviteERecusado() {
        assertThrows(AppException.class,
                () -> service.registrar("N", "g@x.com", "senha12345", Papel.GESTOR_TECNOLOGIA, null));
        assertThrows(AppException.class,
                () -> service.registrar("N", "g@x.com", "senha12345", Papel.GESTOR_TECNOLOGIA, "  "));
        verify(usuarios, never()).save(any());
    }

    @Test
    void conviteInexistenteERecusado() {
        when(convites.findByTokenHash(anyString())).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> service.registrar("N", "g@x.com", "senha12345", Papel.GESTOR_TECNOLOGIA, "falso"));

        assertEquals(403, ex.getCodigo());
    }

    @Test
    void conviteValidoCriaGestorEFicaUsado() {
        String token = criarConviteEDevolverToken("g@x.com");
        ConviteGestor convite = conviteSalvo();
        when(convites.findByTokenHash(anyString())).thenReturn(Optional.of(convite));

        Usuario criado = service.registrar("N", "G@X.com", "senha12345", Papel.GESTOR_TECNOLOGIA, token);

        assertEquals(Papel.GESTOR_TECNOLOGIA, criado.getPapel());
        assertNotNull(convite.getUsadoEm());
    }

    @Test
    void conviteNaoServeParaOutroEmailNemSegundaVez() {
        String token = criarConviteEDevolverToken("g@x.com");
        ConviteGestor convite = conviteSalvo();
        when(convites.findByTokenHash(anyString())).thenReturn(Optional.of(convite));

        assertThrows(AppException.class,
                () -> service.registrar("N", "outro@x.com", "senha12345", Papel.GESTOR_TECNOLOGIA, token));
        service.registrar("N", "g@x.com", "senha12345", Papel.GESTOR_TECNOLOGIA, token);
        assertThrows(AppException.class,
                () -> service.registrar("N", "g@x.com", "senha12345", Papel.GESTOR_TECNOLOGIA, token));
    }

    @Test
    void somenteGestorCriaConvite() {
        UsuarioAutenticado produtor = new UsuarioAutenticado(UUID.randomUUID(), Papel.PRODUTOR_RURAL);
        UsuarioAutenticado agronomo = new UsuarioAutenticado(UUID.randomUUID(), Papel.AGRONOMO);

        assertThrows(AppException.class, () -> service.criarConviteGestor(produtor, "x@x.com"));
        assertThrows(AppException.class, () -> service.criarConviteGestor(agronomo, "x@x.com"));
        verify(convites, never()).save(any());
    }

    @Test
    void produtorEAgronomoRegistramSemConvite() {
        assertEquals(Papel.PRODUTOR_RURAL,
                service.registrar("N", "p@x.com", "senha12345", Papel.PRODUTOR_RURAL, null).getPapel());
        assertEquals(Papel.AGRONOMO,
                service.registrar("N", "a@x.com", "senha12345", Papel.AGRONOMO, null).getPapel());
    }
}
