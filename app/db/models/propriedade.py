import uuid

from geoalchemy2 import Geometry
from sqlalchemy import ForeignKey, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.db.session import Base


class Propriedade(Base):
    __tablename__ = "propriedades"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    nome: Mapped[str] = mapped_column(String(100), nullable=False)
    proprietario_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("usuarios.id"), nullable=False
    )
    # Opcional (RD001/spec 005): nem toda propriedade tem perímetro próprio desenhado.
    # MultiPolygon desde o Escopo V3 (2026-09-03) — era Polygon.
    geometria: Mapped[str | None] = mapped_column(
        Geometry(geometry_type="MULTIPOLYGON", srid=4326), nullable=True
    )
