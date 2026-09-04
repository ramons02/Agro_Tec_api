"""municipio na propriedade (busca por cidade)

Revision ID: 0011
Revises: 0010
Create Date: 2026-09-04
"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "0011"
down_revision: str | None = "0010"
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None


def upgrade() -> None:
    op.add_column("propriedades", sa.Column("municipio", sa.String(length=100), nullable=True))


def downgrade() -> None:
    op.drop_column("propriedades", "municipio")
