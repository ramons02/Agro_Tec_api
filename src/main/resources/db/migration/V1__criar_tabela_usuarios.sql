-- Porte 1:1 de alembic/versions/0001_criar_tabela_usuarios.py
CREATE TABLE usuarios (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    senha_hash VARCHAR(255) NOT NULL,
    papel VARCHAR(30) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ix_usuarios_email ON usuarios (email);
