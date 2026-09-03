import uuid
from datetime import date

from sqlalchemy import Date, Enum, ForeignKey, Index, Numeric, UniqueConstraint, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.core.calculos.status_plantio import StatusPlantio
from app.db.session import Base


class BalancoHidricoDiario(Base):
    __tablename__ = "balanco_hidrico_diario"
    __table_args__ = (
        UniqueConstraint("talhao_id", "data", name="uq_balanco_talhao_data"),
        Index("idx_balanco_talhao_data_desc", "talhao_id", "data"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    talhao_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("talhoes.id", ondelete="CASCADE"), nullable=False
    )
    data: Mapped[date] = mapped_column(Date, nullable=False)
    armazenamento_mm: Mapped[float] = mapped_column(Numeric(6, 2), nullable=False)
    precipitacao_mm: Mapped[float] = mapped_column(Numeric(5, 2), nullable=False)
    evapotranspiracao_mm: Mapped[float] = mapped_column(Numeric(5, 2), nullable=False)
    status_plantio: Mapped[StatusPlantio] = mapped_column(
        Enum(StatusPlantio, name="status_plantio"), nullable=False
    )
