-- Porte 1:1 de alembic/versions/0002_criar_tabelas_inmet.py
-- Extensao postgis ja existe no banco compartilhado (instalada pelo setup do Python);
-- criar aqui exigiria superuser, que o role de app nao tem.
CREATE TABLE estacoes_inmet (
    codigo VARCHAR(10) PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    estado VARCHAR(2) NOT NULL DEFAULT 'PA',
    posicao geometry(Point, 4326) NOT NULL
);

-- GeoAlchemy2 criava este indice GiST implicitamente; aqui e explicito.
CREATE INDEX idx_estacoes_inmet_posicao ON estacoes_inmet USING GIST (posicao);

CREATE TABLE medicoes_clima (
    id BIGSERIAL PRIMARY KEY,
    estacao_codigo VARCHAR(10) NOT NULL REFERENCES estacoes_inmet (codigo),
    data_hora_utc TIMESTAMPTZ NOT NULL,
    precipitacao_mm NUMERIC(5, 2),
    temperatura_c NUMERIC(4, 2),
    umidade_pct NUMERIC(4, 2),
    vento_velocidade_ms NUMERIC(4, 2),
    vento_rajada_ms NUMERIC(4, 2),
    fonte_dados VARCHAR(20) NOT NULL,
    CONSTRAINT uq_medicao_estacao_instante UNIQUE (estacao_codigo, data_hora_utc)
);

CREATE INDEX idx_medicoes_clima_estacao_codigo ON medicoes_clima (estacao_codigo);
-- Ordem DESC no segundo campo nao e expressavel via @Index do Hibernate; fica so no Flyway.
CREATE INDEX idx_medicoes_estacao_data ON medicoes_clima (estacao_codigo, data_hora_utc DESC);
