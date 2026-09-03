"""criar tabela usuarios

Revision ID: 0001
Revises:
Create Date: 2026-09-03
"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

revision: str = "0001"
down_revision: str | None = None
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None

papel_usuario = postgresql.ENUM(
    "PRODUTOR_RURAL", "AGRONOMO", "GESTOR_TECNOLOGIA", name="papel_usuario"
)


def upgrade() -> None:
    papel_usuario.create(op.get_bind(), checkfirst=True)
    op.create_table(
        "usuarios",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("email", sa.String(length=255), nullable=False),
        sa.Column("senha_hash", sa.String(length=255), nullable=False),
        sa.Column("papel", papel_usuario, nullable=False),
        sa.Column("criado_em", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_usuarios_email", "usuarios", ["email"], unique=True)


def downgrade() -> None:
    op.drop_index("ix_usuarios_email", table_name="usuarios")
    op.drop_table("usuarios")
    papel_usuario.drop(op.get_bind(), checkfirst=True)
