-- Porte 1:1 de alembic/versions/0004_adicionar_colunas_solo_talhao.py
ALTER TABLE talhoes
    ADD COLUMN tipo_solo VARCHAR(20),
    ADD COLUMN fracao_argila_pct NUMERIC(5, 2),
    ADD COLUMN fracao_areia_pct NUMERIC(5, 2),
    ADD COLUMN fracao_silte_pct NUMERIC(5, 2),
    ADD COLUMN materia_organica_pct NUMERIC(5, 2),
    ADD COLUMN capacidade_agua_disponivel_mm NUMERIC(6, 2);
