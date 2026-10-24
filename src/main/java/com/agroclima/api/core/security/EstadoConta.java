package com.agroclima.api.core.security;

import com.agroclima.api.business.auth.StatusConta;

/** Estado da conta consultado pelo filtro de autenticacao a cada requisicao (spec 022, R1). */
public record EstadoConta(StatusConta status, int versaoSessao, boolean precisaTrocarSenha) {}
