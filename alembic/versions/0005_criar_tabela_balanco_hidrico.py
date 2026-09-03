"""criar tabela balanco_hidrico_diario (feature 010)

Revision ID: 0005
Revises: 0004
Create Date: 2026-09-03
"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

revision: str = "0005"
down_revision: str | None = "0004"
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "balanco_hidrico_diario",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "talhao_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("talhoes.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("data", sa.Date(), nullable=False),
        sa.Column("armazenamento_mm", sa.Numeric(6, 2), nullable=False),
        sa.Column("precipitacao_mm", sa.Numeric(5, 2), nullable=False),
        sa.Column("evapotranspiracao_mm", sa.Numeric(5, 2), nullable=False),
        sa.UniqueConstraint("talhao_id", "data", name="uq_balanco_talhao_data"),
    )
    op.create_index(
        "idx_balanco_talhao_data_desc", "balanco_hidrico_diario", ["talhao_id", "data"]
    )


def downgrade() -> None:
    op.drop_index("idx_balanco_talhao_data_desc", table_name="balanco_hidrico_diario")
    op.drop_table("balanco_hidrico_diario")
