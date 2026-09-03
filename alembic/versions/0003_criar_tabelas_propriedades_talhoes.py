"""criar tabelas propriedades e talhoes

Revision ID: 0003
Revises: 0002
Create Date: 2026-09-03
"""

from collections.abc import Sequence

import sqlalchemy as sa
from geoalchemy2 import Geometry
from sqlalchemy.dialects import postgresql

from alembic import op

revision: str = "0003"
down_revision: str | None = "0002"
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "propriedades",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("nome", sa.String(length=100), nullable=False),
        sa.Column(
            "proprietario_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("usuarios.id"),
            nullable=False,
        ),
        sa.Column("geometria", Geometry(geometry_type="POLYGON", srid=4326), nullable=True),
    )

    op.create_table(
        "talhoes",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "propriedade_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("propriedades.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("nome", sa.String(length=50), nullable=False),
        sa.Column("geometria", Geometry(geometry_type="POLYGON", srid=4326), nullable=False),
        sa.Column("area_ha", sa.Numeric(10, 4), nullable=False),
    )
    # Índices GiST em "geometria" (ambas as tabelas) já criados automaticamente
    # pelo GeoAlchemy2 (Geometry.spatial_index=True por padrão — ver 0002).


def downgrade() -> None:
    op.drop_table("talhoes")
    op.drop_table("propriedades")
