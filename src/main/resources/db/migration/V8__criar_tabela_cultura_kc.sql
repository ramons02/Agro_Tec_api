-- Porte 1:1 de alembic/versions/0008_criar_tabela_cultura_kc.py (Escopo V3, RD010/RN023)
-- Tabela nasce vazia -- seed de valores reais da FAO fica pendente (mesma nota do Python).
CREATE TABLE cultura_kc (
    id SERIAL PRIMARY KEY,
    cultura VARCHAR(50) NOT NULL,
    fase_fenologica VARCHAR(50) NOT NULL,
    dae_inicio INTEGER NOT NULL,
    dae_fim INTEGER NOT NULL,
    kc_valor NUMERIC(3, 2) NOT NULL
);

CREATE INDEX idx_cultura_kc_cultura ON cultura_kc (cultura);
