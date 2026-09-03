from sqlalchemy import Integer, Numeric, String
from sqlalchemy.orm import Mapped, mapped_column

from app.db.session import Base


class CulturaKc(Base):
    """RD010/RN023 (Escopo V3) — Kc por cultura e faixa de DAE (Dias Após
    Emergência), tabela pública da FAO."""

    __tablename__ = "cultura_kc"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    cultura: Mapped[str] = mapped_column(String(50), nullable=False, index=True)
    fase_fenologica: Mapped[str] = mapped_column(String(50), nullable=False)
    dae_inicio: Mapped[int] = mapped_column(Integer, nullable=False)
    dae_fim: Mapped[int] = mapped_column(Integer, nullable=False)
    kc_valor: Mapped[float] = mapped_column(Numeric(3, 2), nullable=False)
