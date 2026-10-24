-- Produtor e agronomo que esqueceram a senha pedem na tela de login; o pedido aparece como pendencia
-- para o gestor, que redefine a senha. Nulo = sem pedido pendente.
ALTER TABLE usuarios ADD COLUMN senha_esquecida_em TIMESTAMPTZ;

-- O pedido tambem entra na trilha de auditoria (acao SENHA_ESQUECIDA).
ALTER TABLE auditoria_contas DROP CONSTRAINT ck_auditoria_contas_acao;
ALTER TABLE auditoria_contas ADD CONSTRAINT ck_auditoria_contas_acao CHECK (acao IN
    ('CONFIRMAR', 'RECUSAR', 'CRIAR', 'SUSPENDER', 'REATIVAR', 'REDEFINIR_SENHA', 'EDITAR', 'SENHA_ESQUECIDA'));
