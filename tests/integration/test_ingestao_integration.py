from datetime import UTC, datetime

import pytest
from geoalchemy2.functions import ST_SetSRID
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.estacao_inmet import EstacaoInmet
from app.db.models.medicao_clima import FonteDados, MedicaoClima
from app.services.ingestao_service import _persistir_medicao
from tests.integration.conftest import limpar_tabelas


@pytest.fixture
def instante() -> datetime:
    return datetime(2026, 9, 3, 12, 0, tzinfo=UTC)


async def _criar_estacao_teste(db: AsyncSession) -> None:
    ponto = ST_SetSRID(func.ST_MakePoint(-48.4902, -1.4557), 4326)
    db.add(EstacaoInmet(codigo="A201-TESTE", nome="Estação de Teste", estado="PA", posicao=ponto))
    await db.commit()


@pytest.mark.asyncio
async def test_persistir_medicao_grava_no_postgres_real(pg_session: AsyncSession, instante):
    await limpar_tabelas(pg_session, "medicoes_clima", "estacoes_inmet")
    await _criar_estacao_teste(pg_session)

    await _persistir_medicao(
        pg_session,
        estacao_codigo="A201-TESTE",
        data_hora_utc=instante,
        precipitacao_mm=12.4,
        temperatura_c=28.0,
        umidade_pct=65.0,
        vento_velocidade_ms=2.0,
        vento_rajada_ms=4.0,
        fonte_dados=FonteDados.AO_VIVO,
    )
    await pg_session.commit()

    linhas = (await pg_session.execute(select(MedicaoClima))).scalars().all()
    assert len(linhas) == 1
    assert linhas[0].estacao_codigo == "A201-TESTE"
    assert float(linhas[0].precipitacao_mm) == 12.4


@pytest.mark.asyncio
async def test_persistir_medicao_nao_duplica_mesmo_instante(pg_session: AsyncSession, instante):
    """Cenário 3 do quickstart.md — roda a ingestão duas vezes, sem novo dado."""
    await limpar_tabelas(pg_session, "medicoes_clima", "estacoes_inmet")
    await _criar_estacao_teste(pg_session)

    for _ in range(2):
        await _persistir_medicao(
            pg_session,
            estacao_codigo="A201-TESTE",
            data_hora_utc=instante,
            precipitacao_mm=12.4,
            temperatura_c=28.0,
            umidade_pct=65.0,
            vento_velocidade_ms=2.0,
            vento_rajada_ms=4.0,
            fonte_dados=FonteDados.AO_VIVO,
        )
        await pg_session.commit()

    linhas = (await pg_session.execute(select(MedicaoClima))).scalars().all()
    assert len(linhas) == 1, "a mesma (estacao, instante) não deve gerar duas linhas"


@pytest.mark.asyncio
async def test_geometria_point_persiste_e_le_coordenadas_corretas(pg_session: AsyncSession):
    await limpar_tabelas(pg_session, "medicoes_clima", "estacoes_inmet")
    await _criar_estacao_teste(pg_session)

    from geoalchemy2.functions import ST_X, ST_Y

    estacao = (
        await pg_session.execute(select(EstacaoInmet).where(EstacaoInmet.codigo == "A201-TESTE"))
    ).scalar_one()
    resultado = await pg_session.execute(select(ST_Y(estacao.posicao), ST_X(estacao.posicao)))
    latitude, longitude = resultado.one()

    assert round(latitude, 4) == -1.4557
    assert round(longitude, 4) == -48.4902
