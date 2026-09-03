"""adicionar status_plantio ao balanco_hidrico_diario (feature 011)

Revision ID: 0006
Revises: 0005
Create Date: 2026-09-03
"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

revision: str = "0006"
down_revision: str | None = "0005"
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None

status_plantio = postgresql.ENUM(
    "VERDE", "AMARELO", "VERMELHO", name="status_plantio", create_type=False
)


def upgrade() -> None:
    status_plantio.create(op.get_bind(), checkfirst=True)
    op.add_column(
        "balanco_hidrico_diario",
        sa.Column("status_plantio", status_plantio, nullable=False, server_default="AMARELO"),
    )
    op.alter_column("balanco_hidrico_diario", "status_plantio", server_default=None)


def downgrade() -> None:
    op.drop_column("balanco_hidrico_diario", "status_plantio")
    status_plantio.drop(op.get_bind(), checkfirst=True)
