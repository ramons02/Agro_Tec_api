-- Renomear e excluir grupo (Master): as duas acoes entram na auditoria de contas.
ALTER TABLE auditoria_contas DROP CONSTRAINT ck_auditoria_contas_acao;
ALTER TABLE auditoria_contas ADD CONSTRAINT ck_auditoria_contas_acao CHECK (acao IN
    ('CONFIRMAR', 'RECUSAR', 'CRIAR', 'SUSPENDER', 'REATIVAR', 'REDEFINIR_SENHA', 'EDITAR', 'SENHA_ESQUECIDA',
     'CRIAR_ADMIN', 'MOVER_GRUPO', 'REATRIBUIR_GRUPO', 'REMOVER_DO_GRUPO', 'NOVO_LINK_GRUPO',
     'RENOMEAR_GRUPO', 'EXCLUIR_GRUPO'));
