package com.agroclima.api.core.config;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Campo de texto so aceita texto. Por padrao o Jackson converte numero e booleano em String sem avisar
 * ({@code "nome": 123} virava "123"), o que esconde erro de quem chama a API (auditoria de 2026-10-24).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer textoSoAceitaTexto() {
        return builder -> builder.postConfigurer(mapper -> {
            var texto = mapper.coercionConfigFor(LogicalType.Textual);
            texto.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            texto.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            texto.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
    }
}
