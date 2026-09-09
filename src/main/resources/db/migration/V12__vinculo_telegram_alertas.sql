-- Feature 017 (Alertas via Telegram, HU-17): vinculo conta-chat + estado de alerta por talhao.

ALTER TABLE usuarios ADD COLUMN telegram_chat_id VARCHAR(64);

CREATE TABLE tokens_vinculo_telegram (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    token VARCHAR(64) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    usado_em TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_token_vinculo_telegram_token ON tokens_vinculo_telegram (token);

CREATE TYPE classificacao_pulverizacao AS ENUM (
    'FAVORAVEL', 'BLOQUEIO_VENTO_FORTE', 'BLOQUEIO_INVERSAO_TERMICA', 'BLOQUEIO_EVAPORACAO_EXCESSIVA'
);

CREATE TABLE estado_alerta_pulverizacao (
    talhao_id UUID PRIMARY KEY REFERENCES talhoes (id) ON DELETE CASCADE,
    ultima_classificacao classificacao_pulverizacao NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL
);
