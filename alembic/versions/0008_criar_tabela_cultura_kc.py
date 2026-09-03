"""criar tabela cultura_kc (Kc dinamico, Escopo V3)

Revision ID: 0008
Revises: 0007
Create Date: 2026-09-03

NOTA: esta migração cria apenas a estrutura da tabela, sem seed de dados.
Os valores reais de Kc por cultura/DAE (tabela pública da FAO, citada no
Escopo V3) precisam ser carregados a partir de uma fonte agronômica
verificada antes do uso em produção — não foram inventados aqui. Enquanto a
tabela estiver vazia (ou sem linha para a cultura/DAE do talhão), o Balanço
Hídrico usa o Kc de fallback de fase inicial (0.4, RN007).
"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "0008"
down_revision: str | None = "0007"
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "cultura_kc",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("cultura", sa.String(length=50), nullable=False),
        sa.Column("fase_fenologica", sa.String(length=50), nullable=False),
        sa.Column("dae_inicio", sa.Integer(), nullable=False),
        sa.Column("dae_fim", sa.Integer(), nullable=False),
        sa.Column("kc_valor", sa.Numeric(3, 2), nullable=False),
    )
    op.create_index("idx_cultura_kc_cultura", "cultura_kc", ["cultura"])


def downgrade() -> None:
    op.drop_index("idx_cultura_kc_cultura", table_name="cultura_kc")
    op.drop_table("cultura_kc")
