"""Fixtures de integração contra Postgres+PostGIS real.

Diferente de tests/conftest.py (SQLite in-memory, usado pelos testes de
unidade/contrato), estes testes exigem um Postgres real com a extensão
PostGIS habilitada — necessário para validar comportamento que o SQLite não
reproduz (colunas Geometry, `ON CONFLICT` sobre constraint real, `<->`/
`ST_Distance`). Configurados via TEST_DATABASE_URL; se a conexão falhar, os
testes são pulados (skip) em vez de falhar, para não quebrar CI sem Postgres.
"""

import os

import pytest_asyncio
import sqlalchemy
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

TEST_DATABASE_URL = os.environ.get(
    "TEST_DATABASE_URL",
    "postgresql+asyncpg://ramon@/agroclima_test?host=/var/run/postgresql",
)


@pytest_asyncio.fixture
async def pg_session():
    import pytest

    engine = create_async_engine(TEST_DATABASE_URL)
    try:
        async with engine.connect() as conn:
            await conn.execute(sqlalchemy.text("SELECT 1"))
    except Exception as exc:  # noqa: BLE001 — qualquer falha de conexão só deve pular o teste
        await engine.dispose()
        pytest.skip(f"Postgres de integração indisponível ({TEST_DATABASE_URL}): {exc}")

    session_factory = async_sessionmaker(engine, expire_on_commit=False)
    async with session_factory() as session:
        yield session
        await session.rollback()

    await engine.dispose()


async def limpar_tabelas(session: AsyncSession, *tabelas: str) -> None:
    for tabela in tabelas:
        await session.execute(sqlalchemy.text(f"TRUNCATE TABLE {tabela} CASCADE"))
    await session.commit()
