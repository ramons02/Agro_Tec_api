import uuid

from geoalchemy2 import Geometry
from sqlalchemy import ForeignKey, Numeric, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.db.session import Base


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
