from geoalchemy2 import Geometry
from sqlalchemy import String
from sqlalchemy.orm import Mapped, mapped_column

from app.db.session import Base


class EstacaoInmet(Base):
    """Estação meteorológica automática do INMET no Pará (RD003).

    `posicao` exige PostGIS habilitado no banco de destino (Princípio IV da
    Constituição) — não é criável/testável em SQLite; testes automatizados
    desta feature cobrem a lógica de serviço (parsing/timeout/fallback) sem
    tocar esta tabela. Validação de ponta a ponta fica para `quickstart.md`
    contra um Postgres+PostGIS real.
    """

    __tablename__ = "estacoes_inmet"

    codigo: Mapped[str] = mapped_column(String(10), primary_key=True)
    nome: Mapped[str] = mapped_column(String(100), nullable=False)
    estado: Mapped[str] = mapped_column(String(2), default="PA")
    posicao: Mapped[str] = mapped_column(Geometry(geometry_type="POINT", srid=4326))
