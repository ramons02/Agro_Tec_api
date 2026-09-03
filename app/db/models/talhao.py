import enum
import uuid

from geoalchemy2 import Geometry
from sqlalchemy import Enum, ForeignKey, Numeric, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.db.session import Base


class TipoSolo(enum.StrEnum):
    """RD005 — três categorias mutuamente exclusivas."""

    ARGILOSO = "ARGILOSO"
    ARENOSO = "ARENOSO"
    MISTO = "MISTO"


class Talhao(Base):
    __tablename__ = "talhoes"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    propriedade_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("propriedades.id", ondelete="CASCADE"), nullable=False
    )
    nome: Mapped[str] = mapped_column(String(50), nullable=False)
    geometria: Mapped[str] = mapped_column(
        Geometry(geometry_type="POLYGON", srid=4326), nullable=False
    )
    # Calculada a partir da geometria na criação (ST_Area), nunca informada manualmente.
    area_ha: Mapped[float] = mapped_column(Numeric(10, 4), nullable=False)

    # Preenchidos automaticamente pela feature 004 (SoilGrids) — nulos se a fonte
    # não tiver cobertura para a coordenada do talhão (FR-006, RN016).
    tipo_solo: Mapped[TipoSolo | None] = mapped_column(Enum(TipoSolo, name="tipo_solo"), nullable=True)
    fracao_argila_pct: Mapped[float | None] = mapped_column(Numeric(5, 2), nullable=True)
    fracao_areia_pct: Mapped[float | None] = mapped_column(Numeric(5, 2), nullable=True)
    fracao_silte_pct: Mapped[float | None] = mapped_column(Numeric(5, 2), nullable=True)
    materia_organica_pct: Mapped[float | None] = mapped_column(Numeric(5, 2), nullable=True)
    capacidade_agua_disponivel_mm: Mapped[float | None] = mapped_column(Numeric(6, 2), nullable=True)
