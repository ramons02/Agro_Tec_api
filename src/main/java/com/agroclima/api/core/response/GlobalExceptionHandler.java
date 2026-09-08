package com.agroclima.api.core.response;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/** Espelha app_error_handler + validation_error_handler de app/main.py. */
@RestControllerAdvice
public class GlobalExceptionHandler {

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
}
