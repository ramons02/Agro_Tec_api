"""criar tabela vinculos_agronomo_propriedade (feature 014)

Revision ID: 0009
Revises: 0008
Create Date: 2026-09-03
"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

revision: str = "0009"
down_revision: str | None = "0008"
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None

estado_vinculo = postgresql.ENUM(
    "CONVIDADO", "ACEITO", "REVOGADO", name="estado_vinculo", create_type=False
)


def upgrade() -> None:
    estado_vinculo.create(op.get_bind(), checkfirst=True)
    op.create_table(
        "vinculos_agronomo_propriedade",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "agronomo_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("usuarios.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "propriedade_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("propriedades.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("estado", estado_vinculo, nullable=False, server_default="CONVIDADO"),
        sa.Column("convidado_em", sa.DateTime(timezone=True), nullable=False),
        sa.Column("aceito_em", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index(
        "idx_vinculo_agronomo_propriedade",
        "vinculos_agronomo_propriedade",
        ["agronomo_id", "propriedade_id"],
    )


def downgrade() -> None:
    op.drop_index("idx_vinculo_agronomo_propriedade", table_name="vinculos_agronomo_propriedade")
    op.drop_table("vinculos_agronomo_propriedade")
    estado_vinculo.drop(op.get_bind(), checkfirst=True)
