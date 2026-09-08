-- Porte 1:1 de alembic/versions/0007_multipolygon_e_cultura_talhao.py (Escopo V3)
ALTER TABLE propriedades ALTER COLUMN geometria TYPE geometry(MultiPolygon, 4326) USING ST_Multi(geometria);
ALTER TABLE talhoes ALTER COLUMN geometria TYPE geometry(MultiPolygon, 4326) USING ST_Multi(geometria);

ALTER TABLE talhoes
    ADD COLUMN cultura VARCHAR(50),
    ADD COLUMN data_plantio DATE;
