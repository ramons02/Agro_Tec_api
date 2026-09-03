import os
import uuid

os.environ.setdefault("DATABASE_URL", "sqlite+aiosqlite:///:memory:")
os.environ.setdefault("JWT_SECRET", "chave-de-teste-nao-usar-em-producao")

import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import StaticPool

from app.core.security import hash_senha
from app.db.models.usuario import Papel, Usuario
from app.db.session import Base, get_db
from app.main import app


@pytest_asyncio.fixture
async def db_session():
    engine = create_async_engine(
        "sqlite+aiosqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    async with engine.begin() as conn:
        # Cria só as tabelas sem coluna PostGIS: Geometry exige spatialite no
        # SQLite (indisponível neste ambiente). Models com geometria (EstacaoInmet,
        # Propriedade, Talhao) são validados contra Postgres+PostGIS real via
        # quickstart.md de cada feature, não por este fixture.
        await conn.run_sync(Base.metadata.create_all, tables=[Usuario.__table__])

    session_factory = async_sessionmaker(engine, expire_on_commit=False)
    async with session_factory() as session:
        yield session

    await engine.dispose()


@pytest_asyncio.fixture
async def client(db_session: AsyncSession):
    async def _get_db_override():
        yield db_session

    app.dependency_overrides[get_db] = _get_db_override
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()


@pytest_asyncio.fixture
async def usuario_teste(db_session: AsyncSession) -> Usuario:
    usuario = Usuario(
        id=uuid.uuid4(),
        email="produtor@exemplo.com",
        senha_hash=await hash_senha("senha-valida-123"),
        papel=Papel.PRODUTOR_RURAL,
    )
    db_session.add(usuario)
    await db_session.commit()
    await db_session.refresh(usuario)
    return usuario
