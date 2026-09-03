import enum
import uuid
from datetime import UTC, datetime

from sqlalchemy import DateTime, Enum, ForeignKey, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.db.session import Base


class EstadoVinculo(enum.StrEnum):
    """RD (feature 014) — convite nunca vale sozinho, precisa de aceite (FR-005)."""

    CONVIDADO = "CONVIDADO"
    ACEITO = "ACEITO"
    REVOGADO = "REVOGADO"


class VinculoAgronomoPropriedade(Base):
    __tablename__ = "vinculos_agronomo_propriedade"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    agronomo_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("usuarios.id", ondelete="CASCADE"), nullable=False
    )
    propriedade_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("propriedades.id", ondelete="CASCADE"), nullable=False
    )
    estado: Mapped[EstadoVinculo] = mapped_column(
        Enum(EstadoVinculo, name="estado_vinculo"), nullable=False, default=EstadoVinculo.CONVIDADO
    )
    convidado_em: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(UTC)
    )
    aceito_em: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
