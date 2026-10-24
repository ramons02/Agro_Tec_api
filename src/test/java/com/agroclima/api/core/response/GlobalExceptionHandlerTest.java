package com.agroclima.api.core.response;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void excecaoInesperadaVira500GenericoSemVazarDetalheInterno() throws Exception {
        ResponseEntity<ErrorEnvelope> resposta = handler.tratarInesperado(
                new IllegalStateException("SELECT * FROM usuarios -- senha=segredo em /opt/app/Config.java"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resposta.getStatusCode());
        String corpo = resposta.getBody().mensagem();
        assertFalse(corpo.contains("SELECT"));
        assertFalse(corpo.contains("segredo"));
        assertFalse(corpo.contains("Config.java"));
        assertFalse(corpo.contains("IllegalStateException"));
    }

    @Test
    void excecoesDeSegurancaSaoRelancadasParaOSpringSecurityResponder() {
        assertThrows(AccessDeniedException.class, () -> handler.tratarInesperado(new AccessDeniedException("negado")));
        assertThrows(BadCredentialsException.class, () -> handler.tratarInesperado(new BadCredentialsException("x")));
    }

    @Test
    void responseStatusExceptionMantemOStatusPedido() {
        ResponseEntity<ErrorEnvelope> resposta =
                handler.tratarStatusExplicito(new ResponseStatusException(HttpStatus.CONFLICT, "conflito de teste"));

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals("conflito de teste", resposta.getBody().mensagem());
    }

    @Test
    void appExceptionContinuaComOCodigoDela() {
        ResponseEntity<ErrorEnvelope> resposta = handler.tratarAppException(new AppException(403, "sem permissao"));

        assertEquals(HttpStatus.FORBIDDEN, resposta.getStatusCode());
        assertEquals("sem permissao", resposta.getBody().mensagem());
    }
}
