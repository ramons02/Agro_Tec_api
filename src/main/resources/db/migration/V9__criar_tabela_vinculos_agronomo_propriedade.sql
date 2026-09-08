-- Porte 1:1 de alembic/versions/0009_criar_tabela_vinculos_agronomo_propriedade.py
CREATE TYPE estado_vinculo AS ENUM ('CONVIDADO', 'ACEITO', 'REVOGADO');

CREATE TABLE vinculos_agronomo_propriedade (
    id UUID PRIMARY KEY,
    agronomo_id UUID NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    propriedade_id UUID NOT NULL REFERENCES propriedades (id) ON DELETE CASCADE,
    estado estado_vinculo NOT NULL DEFAULT 'CONVIDADO',
    convidado_em TIMESTAMPTZ NOT NULL,
    aceito_em TIMESTAMPTZ
);

-- Nao e UNIQUE de proposito: um agronomo pode ter vinculo revogado e depois reconvidado.
CREATE INDEX idx_vinculo_agronomo_propriedade ON vinculos_agronomo_propriedade (agronomo_id, propriedade_id);
