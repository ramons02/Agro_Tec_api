-- Porte 1:1 de alembic/versions/0001_criar_tabela_usuarios.py
-- Enum nativo do Postgres (nao VARCHAR) -- bate exatamente com o schema real ja em
-- producao (criado pelo Alembic), confirmado por introspeccao direta do banco.
CREATE TYPE papel_usuario AS ENUM ('PRODUTOR_RURAL', 'AGRONOMO', 'GESTOR_TECNOLOGIA');

CREATE TABLE usuarios (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    senha_hash VARCHAR(255) NOT NULL,
    papel papel_usuario NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ix_usuarios_email ON usuarios (email);
