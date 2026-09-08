-- Porte 1:1 de alembic/versions/0005_criar_tabela_balanco_hidrico.py
CREATE TABLE balanco_hidrico_diario (
    id UUID PRIMARY KEY,
    talhao_id UUID NOT NULL REFERENCES talhoes (id) ON DELETE CASCADE,
    data DATE NOT NULL,
    armazenamento_mm NUMERIC(6, 2) NOT NULL,
    precipitacao_mm NUMERIC(5, 2) NOT NULL,
    evapotranspiracao_mm NUMERIC(5, 2) NOT NULL,
    CONSTRAINT uq_balanco_talhao_data UNIQUE (talhao_id, data)
);

CREATE INDEX idx_balanco_talhao_data_desc ON balanco_hidrico_diario (talhao_id, data);
