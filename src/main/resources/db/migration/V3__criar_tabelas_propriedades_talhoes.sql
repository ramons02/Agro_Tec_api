-- Porte 1:1 de alembic/versions/0003_criar_tabelas_propriedades_talhoes.py
-- Geometria nasce Polygon aqui de proposito (fidelidade historica); V7 converte pra MultiPolygon.
CREATE TABLE propriedades (
    id UUID PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    proprietario_id UUID NOT NULL REFERENCES usuarios (id),
    geometria geometry(Polygon, 4326)
);

CREATE INDEX idx_propriedades_geometria ON propriedades USING GIST (geometria);

CREATE TABLE talhoes (
    id UUID PRIMARY KEY,
    propriedade_id UUID NOT NULL REFERENCES propriedades (id) ON DELETE CASCADE,
    nome VARCHAR(50) NOT NULL,
    geometria geometry(Polygon, 4326) NOT NULL,
    area_ha NUMERIC(10, 4) NOT NULL
);

CREATE INDEX idx_talhoes_geometria ON talhoes USING GIST (geometria);
CREATE INDEX idx_talhoes_propriedade_id ON talhoes (propriedade_id);
