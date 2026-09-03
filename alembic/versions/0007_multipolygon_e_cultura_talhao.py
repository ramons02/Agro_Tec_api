"""migra geometria para MultiPolygon e adiciona cultura/data_plantio (Escopo V3)

Revision ID: 0007
Revises: 0006
Create Date: 2026-09-03
"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "0007"
down_revision: str | None = "0006"
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None


def upgrade() -> None:
    # ST_Multi() converte Polygon existente em MultiPolygon de uma parte só,
    # preservando dados já cadastrados; geometrias já MultiPolygon ou NULL
    # passam por ST_Multi sem alteração de conteúdo.
    op.execute(
        "ALTER TABLE propriedades ALTER COLUMN geometria TYPE geometry(MultiPolygon, 4326) "
        "USING ST_Multi(geometria)"
    )
    op.execute(
        "ALTER TABLE talhoes ALTER COLUMN geometria TYPE geometry(MultiPolygon, 4326) "
        "USING ST_Multi(geometria)"
    )

    op.add_column("talhoes", sa.Column("cultura", sa.String(length=50), nullable=True))
    op.add_column("talhoes", sa.Column("data_plantio", sa.Date(), nullable=True))


def downgrade() -> None:
    op.drop_column("talhoes", "data_plantio")
    op.drop_column("talhoes", "cultura")

    # Downgrade só é seguro se todos os dados forem geometrias de 1 parte só
    # (ST_GeometryN(geom, 1) descarta partes extras de um MultiPolygon real).
    op.execute(
        "ALTER TABLE talhoes ALTER COLUMN geometria TYPE geometry(Polygon, 4326) "
        "USING ST_GeometryN(geometria, 1)"
    )
    op.execute(
        "ALTER TABLE propriedades ALTER COLUMN geometria TYPE geometry(Polygon, 4326) "
        "USING ST_GeometryN(geometria, 1)"
    )
