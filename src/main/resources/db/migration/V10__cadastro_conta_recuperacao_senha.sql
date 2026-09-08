-- Porte 1:1 de alembic/versions/0010_cadastro_conta_recuperacao_senha.py
ALTER TABLE usuarios ADD COLUMN nome VARCHAR(255);

CREATE TABLE tokens_recuperacao_senha (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    token VARCHAR(64) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    usado_em TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_token_recuperacao_senha_token ON tokens_recuperacao_senha (token);
