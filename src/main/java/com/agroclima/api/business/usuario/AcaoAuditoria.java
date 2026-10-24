package com.agroclima.api.business.usuario;

/** Acoes de gestao de conta registradas em auditoria_contas (spec 022, FR-020). */
public enum AcaoAuditoria {
    CONFIRMAR,
    RECUSAR,
    CRIAR,
    SUSPENDER,
    REATIVAR,
    REDEFINIR_SENHA,
    EDITAR
}
