-- Porte 1:1 de alembic/versions/0006_adicionar_status_plantio_balanco.py
-- Default so pro backfill historico; schema final nao tem default (igual ao Python).
ALTER TABLE balanco_hidrico_diario ADD COLUMN status_plantio VARCHAR(20) NOT NULL DEFAULT 'AMARELO';
ALTER TABLE balanco_hidrico_diario ALTER COLUMN status_plantio DROP DEFAULT;
