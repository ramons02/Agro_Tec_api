import enum
from datetime import datetime

from sqlalchemy import (
    BigInteger,
    DateTime,
    Enum,
    ForeignKey,
    Index,
    Numeric,
    String,
    UniqueConstraint,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.db.session import Base


class FonteDados(enum.StrEnum):
    """RF033 — indica se a medição é do exato momento ou a última válida em cache."""

    AO_VIVO = "AO_VIVO"
    CACHE_EXPIRADO = "CACHE_EXPIRADO"


class MedicaoClima(Base):
    __tablename__ = "medicoes_clima"
    __table_args__ = (
        UniqueConstraint("estacao_codigo", "data_hora_utc", name="uq_medicao_estacao_instante"),
        Index("idx_medicoes_estacao_data", "estacao_codigo", text("data_hora_utc DESC")),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    estacao_codigo: Mapped[str] = mapped_column(
        String(10), ForeignKey("estacoes_inmet.codigo"), nullable=False, index=True
    )
    data_hora_utc: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    precipitacao_mm: Mapped[float] = mapped_column(Numeric(5, 2), nullable=True)
    temperatura_c: Mapped[float] = mapped_column(Numeric(4, 2), nullable=True)
    umidade_pct: Mapped[float] = mapped_column(Numeric(4, 2), nullable=True)
    # Unidade nativa do INMET — conversão para km/h acontece na leitura (feature 008, §research.md)
    vento_velocidade_ms: Mapped[float] = mapped_column(Numeric(4, 2), nullable=True)
    vento_rajada_ms: Mapped[float] = mapped_column(Numeric(4, 2), nullable=True)
    fonte_dados: Mapped[FonteDados] = mapped_column(
        Enum(FonteDados, name="fonte_dados_medicao"), nullable=False
    )
