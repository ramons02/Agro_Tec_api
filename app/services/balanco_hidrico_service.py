"""Feature 010 — job diário do Balanço Hídrico do Solo (RN007).

Precipitação vem da estação INMET mais próxima (medida, não prevista —
`calculos-geo-metero.md` §4B); ET0 vem do Open-Meteo (feature 003) para a
coordenada do talhão. CAD vem da parametrização de solo (feature 004).
"""

import logging
from datetime import UTC, date, datetime, timedelta

from geoalchemy2.functions import ST_X, ST_Y, ST_Centroid
from sqlalchemy import func, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.calculos.balanco_hidrico import armazenamento_inicial, calcular_armazenamento
from app.db.models.balanco_hidrico_diario import BalancoHidricoDiario
from app.db.models.medicao_clima import MedicaoClima
from app.db.models.talhao import Talhao
from app.db.queries.estacao_proxima import buscar_estacao_mais_proxima
from app.services.openmeteo_service import FontePrevisaoIndisponivelError, obter_previsao

logger = logging.getLogger(__name__)


async def _armazenamento_do_dia_anterior(db: AsyncSession, talhao_id, data_alvo: date) -> float | None:
    resultado = await db.execute(
        select(BalancoHidricoDiario.armazenamento_mm)
        .where(BalancoHidricoDiario.talhao_id == talhao_id, BalancoHidricoDiario.data < data_alvo)
        .order_by(BalancoHidricoDiario.data.desc())
        .limit(1)
    )
    valor = resultado.scalar_one_or_none()
    return float(valor) if valor is not None else None


async def _precipitacao_do_dia(db: AsyncSession, estacao_codigo: str, data_alvo: date) -> float:
    """Soma das medições de precipitação da estação no dia (RN007, P_i)."""
    inicio = datetime.combine(data_alvo, datetime.min.time(), tzinfo=UTC)
    fim = inicio + timedelta(days=1)
    resultado = await db.execute(
        select(func.coalesce(func.sum(MedicaoClima.precipitacao_mm), 0)).where(
            MedicaoClima.estacao_codigo == estacao_codigo,
            MedicaoClima.data_hora_utc >= inicio,
            MedicaoClima.data_hora_utc < fim,
        )
    )
    return float(resultado.scalar_one())


async def _centroide_lat_long(db: AsyncSession, talhao: Talhao) -> tuple[float, float]:
    resultado = await db.execute(
        select(ST_Y(ST_Centroid(talhao.geometria)), ST_X(ST_Centroid(talhao.geometria)))
    )
    return resultado.one()


async def calcular_balanco_hidrico_do_talhao(
    db: AsyncSession, talhao: Talhao, data_alvo: date | None = None
) -> BalancoHidricoDiario | None:
    """Retorna None se o talhão ainda não tem CAD (feature 004 não parametrizou) ou
    não há estação disponível — sem CAD/estação, o cálculo não é possível ainda."""
    if talhao.capacidade_agua_disponivel_mm is None:
        return None

    data_alvo = data_alvo or datetime.now(UTC).date()
    cad_mm = float(talhao.capacidade_agua_disponivel_mm)

    resultado_estacao = await buscar_estacao_mais_proxima(db, talhao)
    if resultado_estacao is None:
        return None

    arm_anterior = await _armazenamento_do_dia_anterior(db, talhao.id, data_alvo)
    if arm_anterior is None:
        arm_anterior = armazenamento_inicial(cad_mm)

    precipitacao_mm = await _precipitacao_do_dia(db, resultado_estacao.estacao_codigo, data_alvo)

    try:
        latitude, longitude = await _centroide_lat_long(db, talhao)
        previsao = await obter_previsao(latitude, longitude)
        et0_mm = previsao.evapotranspiracao_mm
    except FontePrevisaoIndisponivelError:
        logger.warning("ET0 indisponível para talhão %s — usando ET0=0 neste ciclo", talhao.id)
        et0_mm = 0.0

    armazenamento_mm = calcular_armazenamento(arm_anterior, precipitacao_mm, et0_mm, cad_mm)

    stmt = (
        pg_insert(BalancoHidricoDiario)
        .values(
            talhao_id=talhao.id,
            data=data_alvo,
            armazenamento_mm=armazenamento_mm,
            precipitacao_mm=precipitacao_mm,
            evapotranspiracao_mm=et0_mm,
        )
        .on_conflict_do_update(
            constraint="uq_balanco_talhao_data",
            set_={
                "armazenamento_mm": armazenamento_mm,
                "precipitacao_mm": precipitacao_mm,
                "evapotranspiracao_mm": et0_mm,
            },
        )
    )
    await db.execute(stmt)

    return BalancoHidricoDiario(
        talhao_id=talhao.id,
        data=data_alvo,
        armazenamento_mm=armazenamento_mm,
        precipitacao_mm=precipitacao_mm,
        evapotranspiracao_mm=et0_mm,
    )


async def calcular_balanco_hidrico_todos_talhoes(db: AsyncSession) -> int:
    """Job diário — retorna quantos talhões tiveram o balanço calculado com sucesso."""
    talhoes = (await db.execute(select(Talhao))).scalars().all()
    calculados = 0
    for talhao in talhoes:
        resultado = await calcular_balanco_hidrico_do_talhao(db, talhao)
        if resultado is not None:
            calculados += 1
    await db.commit()
    return calculados
