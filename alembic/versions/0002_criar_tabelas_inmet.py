"""criar tabelas estacoes_inmet e medicoes_clima

Revision ID: 0002
Revises: 0001
Create Date: 2026-09-03
"""

from collections.abc import Sequence

import sqlalchemy as sa
from geoalchemy2 import Geometry
from sqlalchemy.dialects import postgresql

from alembic import op

revision: str = "0002"
down_revision: str | None = "0001"
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None

fonte_dados_medicao = postgresql.ENUM(
    "AO_VIVO", "CACHE_EXPIRADO", name="fonte_dados_medicao", create_type=False
)


def upgrade() -> None:
    # Requer superuser (ou role com CREATEDB+privilégio de extensão) na primeira vez;
    # em ambientes gerenciados normalmente já é habilitado pelo DBA antes do deploy.
    op.execute("CREATE EXTENSION IF NOT EXISTS postgis")

    op.create_table(
        "estacoes_inmet",
        sa.Column("codigo", sa.String(length=10), primary_key=True),
        sa.Column("nome", sa.String(length=100), nullable=False),
        sa.Column("estado", sa.String(length=2), server_default="PA"),
        sa.Column("posicao", Geometry(geometry_type="POINT", srid=4326), nullable=False),
    )

    fonte_dados_medicao.create(op.get_bind(), checkfirst=True)
    op.create_table(
        "medicoes_clima",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column(
            "estacao_codigo",
            sa.String(length=10),
            sa.ForeignKey("estacoes_inmet.codigo"),
            nullable=False,
        ),
        sa.Column("data_hora_utc", sa.DateTime(timezone=True), nullable=False),
        sa.Column("precipitacao_mm", sa.Numeric(5, 2)),
        sa.Column("temperatura_c", sa.Numeric(4, 2)),
        sa.Column("umidade_pct", sa.Numeric(4, 2)),
        sa.Column("vento_velocidade_ms", sa.Numeric(4, 2)),
        sa.Column("vento_rajada_ms", sa.Numeric(4, 2)),
        sa.Column("fonte_dados", fonte_dados_medicao, nullable=False),
        sa.UniqueConstraint(
            "estacao_codigo", "data_hora_utc", name="uq_medicao_estacao_instante"
        ),
    )
    op.create_index(
        "idx_medicoes_estacao_data",
        "medicoes_clima",
        ["estacao_codigo", sa.text("data_hora_utc DESC")],
    )
    # GeoAlchemy2 já cria o índice GiST de "posicao" automaticamente ao criar a
    # tabela (Geometry.spatial_index=True por padrão) — sem necessidade de índice manual.


def downgrade() -> None:
    op.drop_index("idx_estacoes_inmet_posicao", table_name="estacoes_inmet")
    op.drop_index("idx_medicoes_estacao_data", table_name="medicoes_clima")
    op.drop_table("medicoes_clima")
    fonte_dados_medicao.drop(op.get_bind(), checkfirst=True)
    op.drop_table("estacoes_inmet")
