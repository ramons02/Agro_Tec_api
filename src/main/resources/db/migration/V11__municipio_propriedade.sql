-- Porte 1:1 de alembic/versions/0011_municipio_propriedade.py
ALTER TABLE propriedades ADD COLUMN municipio VARCHAR(100);
