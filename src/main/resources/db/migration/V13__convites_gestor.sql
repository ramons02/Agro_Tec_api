-- Convite de uso unico para criar conta GESTOR_TECNOLOGIA (registro publico nao aceita mais esse papel).
-- Guarda so o hash SHA-256 do token; o token em claro existe apenas no link entregue ao convidado.
CREATE TABLE convites_gestor (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    criado_por UUID NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    criado_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    usado_em TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_convite_gestor_token_hash ON convites_gestor (token_hash);
