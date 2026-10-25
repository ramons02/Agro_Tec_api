-- Notificacao no celular (Web Push) com o PWA fechado: uma assinatura por navegador/aparelho.
CREATE TABLE assinaturas_push (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    endpoint TEXT NOT NULL UNIQUE,
    p256dh TEXT NOT NULL,
    auth TEXT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_assinaturas_push_usuario ON assinaturas_push (usuario_id);

-- Par de chaves VAPID da instancia (identifica a API perante o servico de push). Uma linha so.
CREATE TABLE config_push (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    chave_publica TEXT NOT NULL,
    chave_privada TEXT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL
);
