package com.agroclima.api.core.response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

/**
 * Espelha app_error_handler + validation_error_handler de app/main.py. Os erros do proprio Spring
 * (corpo malformado, parametro invalido, rota ou metodo inexistente...) saem no mesmo envelope e com o
 * status certo; antes caiam no despacho de erro e chegavam ao usuario autenticado como 401.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorEnvelope> tratarAppException(AppException ex) {
        return ResponseEntity.status(ex.getCodigo())
                .body(ErrorEnvelope.of(ex.getCodigo(), ex.getMessage(), ex.getDetalhes()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> tratarValidacao(MethodArgumentNotValidException ex) {
        List<Map<String, String>> detalhes = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> Map.of("campo", erro.getField(), "mensagem", String.valueOf(erro.getDefaultMessage())))
                .toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorEnvelope.of(422, "Dados de entrada inválidos.", detalhes));
    }

    /** Arquivo ou GeoJSON de geometria que nao da para ler: as mensagens sao fixas e escritas para quem enviou. */
    @ExceptionHandler(com.agroclima.api.core.geo.GeometriaInvalidaException.class)
    public ResponseEntity<ErrorEnvelope> tratarGeometriaInvalida(com.agroclima.api.core.geo.GeometriaInvalidaException ex) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> tratarCorpoIlegivel(HttpMessageNotReadableException ex) {
        return resposta(HttpStatus.BAD_REQUEST, "Corpo da requisição ausente ou malformado.");
    }

    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class
    })
    public ResponseEntity<ErrorEnvelope> tratarParametroInvalido(Exception ex) {
        return resposta(HttpStatus.BAD_REQUEST, "Parâmetro da requisição ausente ou inválido.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorEnvelope> tratarRotaInexistente(NoResourceFoundException ex) {
        return resposta(HttpStatus.NOT_FOUND, "Recurso não encontrado.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> tratarMetodoNaoSuportado(HttpRequestMethodNotSupportedException ex) {
        return resposta(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP não suportado nesta rota.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> tratarMidiaNaoSuportada(HttpMediaTypeNotSupportedException ex) {
        return resposta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de conteúdo não suportado.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorEnvelope> tratarArquivoGrande(MaxUploadSizeExceededException ex) {
        return resposta(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo maior que o tamanho permitido.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorEnvelope> tratarStatusExplicito(ResponseStatusException ex) {
        return resposta(HttpStatus.valueOf(ex.getStatusCode().value()),
                ex.getReason() != null ? ex.getReason() : "Erro na requisição.");
    }

    /**
     * Qualquer excecao inesperada vira 500 com mensagem generica -- nada de classe, SQL ou caminho
     * interno no corpo; o detalhe vai so para o log. Excecoes de seguranca sao relancadas para o
     * Spring Security responder 401/403 como sempre.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> tratarInesperado(Exception ex) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("Erro inesperado: {}", ex.toString(), ex);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Tente novamente em instantes.");
    }

    private ResponseEntity<ErrorEnvelope> resposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(ErrorEnvelope.of(status.value(), mensagem, null));
    }
}
