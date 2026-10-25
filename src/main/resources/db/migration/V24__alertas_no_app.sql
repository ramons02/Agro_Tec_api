-- Alertas que aparecem dentro do app (sino), no lugar do Telegram: um registro por pessoa e por aviso.
CREATE TABLE alertas (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    talhao_id UUID NOT NULL REFERENCES talhoes (id) ON DELETE CASCADE,
    tipo VARCHAR(30) NOT NULL,
    mensagem TEXT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    lido_em TIMESTAMPTZ,
    CONSTRAINT ck_alertas_tipo CHECK (tipo IN ('JANELA_FAVORAVEL', 'RAJADA_FORTE'))
);

CREATE INDEX ix_alertas_usuario_criado ON alertas (usuario_id, criado_em DESC);
