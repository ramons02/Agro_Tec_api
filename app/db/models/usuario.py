import enum
import uuid
from datetime import UTC, datetime

from sqlalchemy import DateTime, Enum, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.db.session import Base


class Papel(enum.StrEnum):
    """RD009 — três papéis mutuamente exclusivos."""

    PRODUTOR_RURAL = "PRODUTOR_RURAL"
    AGRONOMO = "AGRONOMO"
    GESTOR_TECNOLOGIA = "GESTOR_TECNOLOGIA"


class Usuario(Base):
    __tablename__ = "usuarios"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    email: Mapped[str] = mapped_column(String(255), unique=True, nullable=False, index=True)
    senha_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    papel: Mapped[Papel] = mapped_column(Enum(Papel, name="papel_usuario"), nullable=False)
    criado_em: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(UTC)
    )
