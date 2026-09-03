"""cadastro de conta e recuperacao de senha (feature 013)

Revision ID: 0010
Revises: 0009
Create Date: 2026-09-03
"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

revision: str = "0010"
down_revision: str | None = "0009"
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None


def upgrade() -> None:
    op.add_column("usuarios", sa.Column("nome", sa.String(length=255), nullable=True))

    op.create_table(
        "tokens_recuperacao_senha",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "usuario_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("usuarios.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("token", sa.String(length=64), nullable=False, unique=True),
        sa.Column("criado_em", sa.DateTime(timezone=True), nullable=False),
        sa.Column("expira_em", sa.DateTime(timezone=True), nullable=False),
        sa.Column("usado_em", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index(
        "idx_token_recuperacao_senha_token", "tokens_recuperacao_senha", ["token"], unique=True
    )


def downgrade() -> None:
    op.drop_index("idx_token_recuperacao_senha_token", table_name="tokens_recuperacao_senha")
    op.drop_table("tokens_recuperacao_senha")
    op.drop_column("usuarios", "nome")
