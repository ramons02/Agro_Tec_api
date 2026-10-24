package com.agroclima.api.core.security;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.StatusConta;

import java.util.List;
import java.util.UUID;

/**
 * Estado da conta consultado pelo filtro de autenticacao a cada requisicao (spec 022, R1; spec 024, R3):
 * alem do estado e da versao de sessao, o papel e o escopo de grupo vem do banco e nao do token, para uma
 * mudanca de papel ou de grupo valer em ate 5 s mesmo com o token de 24 h ainda em uso.
 *
 * @param grupoId grupo do produtor e do Admin; nulo para agronomo e para pendente sem grupo
 * @param gruposDoAgronomo grupos do agronomo (vazio para os demais papeis)
 */
public record EstadoConta(
        StatusConta status,
        int versaoSessao,
        boolean precisaTrocarSenha,
        Papel papel,
        UUID grupoId,
        List<UUID> gruposDoAgronomo) {}
