package com.agroclima.api.business.usuario;

/** Acoes de gestao de conta registradas em auditoria_contas (spec 022, FR-020). */
public enum AcaoAuditoria {
    CONFIRMAR,
    RECUSAR,
    CRIAR,
    SUSPENDER,
    REATIVAR,
    REDEFINIR_SENHA,
    EDITAR,
    SENHA_ESQUECIDA,
    CRIAR_ADMIN,
    MOVER_GRUPO,
    REATRIBUIR_GRUPO,
    REMOVER_DO_GRUPO,
    NOVO_LINK_GRUPO
}
