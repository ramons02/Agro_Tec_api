"""adicionar colunas de solo ao talhao (feature 004)

Revision ID: 0004
Revises: 0003
Create Date: 2026-09-03
"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

revision: str = "0004"
down_revision: str | None = "0003"
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None

tipo_solo = postgresql.ENUM("ARGILOSO", "ARENOSO", "MISTO", name="tipo_solo", create_type=False)


def upgrade() -> None:
    tipo_solo.create(op.get_bind(), checkfirst=True)
    op.add_column("talhoes", sa.Column("tipo_solo", tipo_solo, nullable=True))
    op.add_column("talhoes", sa.Column("fracao_argila_pct", sa.Numeric(5, 2), nullable=True))
    op.add_column("talhoes", sa.Column("fracao_areia_pct", sa.Numeric(5, 2), nullable=True))
    op.add_column("talhoes", sa.Column("fracao_silte_pct", sa.Numeric(5, 2), nullable=True))
    op.add_column("talhoes", sa.Column("materia_organica_pct", sa.Numeric(5, 2), nullable=True))
    op.add_column(
        "talhoes", sa.Column("capacidade_agua_disponivel_mm", sa.Numeric(6, 2), nullable=True)
    )


def downgrade() -> None:
    op.drop_column("talhoes", "capacidade_agua_disponivel_mm")
    op.drop_column("talhoes", "materia_organica_pct")
    op.drop_column("talhoes", "fracao_silte_pct")
    op.drop_column("talhoes", "fracao_areia_pct")
    op.drop_column("talhoes", "tipo_solo")
    tipo_solo.drop(op.get_bind(), checkfirst=True)
