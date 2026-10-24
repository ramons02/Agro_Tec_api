-- Spec 022 (HU-19): ciclo de vida da conta (PENDENTE, ATIVA, SUSPENSA), senha temporaria com
-- troca obrigatoria, versao de sessao e trilha de auditoria. Aditiva: contas existentes viram
-- ATIVA, sem troca obrigatoria (os padroes das colunas fazem a migracao dos dados).
ALTER TABLE usuarios
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ATIVA',
    ADD COLUMN precisa_trocar_senha BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN senha_temporaria_expira_em TIMESTAMPTZ,
    ADD COLUMN versao_sessao INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN motivo_suspensao VARCHAR(500),
    ADD COLUMN suspensa_em TIMESTAMPTZ,
    -- Produtor e agronomo que esqueceram a senha pedem na tela de login; o pedido aparece para o gestor.
    ADD COLUMN senha_esquecida_em TIMESTAMPTZ,
    ALTER COLUMN senha_hash DROP NOT NULL,
    ADD CONSTRAINT ck_usuarios_status CHECK (status IN ('PENDENTE', 'ATIVA', 'SUSPENSA')),
    ADD CONSTRAINT ck_usuarios_pendente_sem_senha CHECK (status <> 'PENDENTE' OR senha_hash IS NULL),
    ADD CONSTRAINT ck_usuarios_nao_pendente_com_senha CHECK (status = 'PENDENTE' OR senha_hash IS NOT NULL);

-- Sem chave estrangeira de proposito: o registro de uma recusa precisa sobreviver a conta apagada,
-- e o autor continua listado mesmo que a conta dele mude.
CREATE TABLE auditoria_contas (
    id UUID PRIMARY KEY,
    ator_id UUID NOT NULL,
    acao VARCHAR(20) NOT NULL,
    alvo_id UUID,
    alvo_email VARCHAR(255) NOT NULL,
    detalhe VARCHAR(500),
    criado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_auditoria_contas_acao CHECK (acao IN
        ('CONFIRMAR', 'RECUSAR', 'CRIAR', 'SUSPENDER', 'REATIVAR', 'REDEFINIR_SENHA', 'EDITAR', 'SENHA_ESQUECIDA'))
);

CREATE INDEX ix_auditoria_contas_alvo_id ON auditoria_contas (alvo_id, criado_em DESC);
CREATE INDEX ix_auditoria_contas_alvo_email ON auditoria_contas (alvo_email, criado_em DESC);
