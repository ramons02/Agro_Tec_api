import logging
from dataclasses import dataclass
from datetime import UTC, datetime

from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.estacao_inmet import EstacaoInmet
from app.db.models.medicao_clima import FonteDados, MedicaoClima
from app.services import inmet_service, openmeteo_service
from app.services.inmet_service import FonteIndisponivelError
from app.services.openmeteo_service import FontePrevisaoIndisponivelError

logger = logging.getLogger(__name__)


@dataclass
class ResumoIngestao:
    estacoes_com_sucesso: int = 0
    estacoes_com_fallback: int = 0
    estacoes_com_falha_total: int = 0


async def _persistir_medicao(
    db: AsyncSession,
    estacao_codigo: str,
    data_hora_utc: datetime,
    precipitacao_mm: float | None,
    temperatura_c: float | None,
    umidade_pct: float | None,
    vento_velocidade_ms: float | None,
    vento_rajada_ms: float | None,
    fonte_dados: FonteDados,
) -> None:
    """Upsert idempotente — mesma (estacao, instante) nunca duplica (research.md)."""
    stmt = (
        pg_insert(MedicaoClima)
        .values(
            estacao_codigo=estacao_codigo,
            data_hora_utc=data_hora_utc,
            precipitacao_mm=precipitacao_mm,
            temperatura_c=temperatura_c,
            umidade_pct=umidade_pct,
            vento_velocidade_ms=vento_velocidade_ms,
            vento_rajada_ms=vento_rajada_ms,
            fonte_dados=fonte_dados,
        )
        .on_conflict_do_nothing(constraint="uq_medicao_estacao_instante")
    )
    await db.execute(stmt)


async def ingerir_estacao(db: AsyncSession, estacao: EstacaoInmet) -> str:
    """Busca a medição mais recente de uma estação; INMET com fallback Open-Meteo (RN009).

    Retorna "sucesso", "fallback" ou "falha_total" para agregação em ResumoIngestao.
    """
    try:
        medicao = await inmet_service.buscar_medicao_recente(estacao.codigo)
        if medicao is None:
            return "falha_total"
        await _persistir_medicao(
            db,
            estacao_codigo=medicao.estacao_codigo,
            data_hora_utc=medicao.data_hora_utc,
            precipitacao_mm=medicao.precipitacao_mm,
            temperatura_c=medicao.temperatura_c,
            umidade_pct=medicao.umidade_pct,
            vento_velocidade_ms=medicao.vento_velocidade_ms,
            vento_rajada_ms=medicao.vento_rajada_ms,
            fonte_dados=FonteDados.AO_VIVO,
        )
        return "sucesso"
    except FonteIndisponivelError as erro_inmet:
        logger.warning(
            "Fallback para Open-Meteo acionado: estacao=%s motivo=%s instante=%s",
            estacao.codigo,
            erro_inmet,
            datetime.now(UTC).isoformat(),
        )
        try:
            latitude, longitude = await _coordenadas_estacao(db, estacao)
            previsao = await openmeteo_service.obter_previsao(latitude, longitude)
        except FontePrevisaoIndisponivelError:
            logger.error(
                "Fallback também falhou para estacao=%s — sem medicao neste ciclo",
                estacao.codigo,
            )
            return "falha_total"

        await _persistir_medicao(
            db,
            estacao_codigo=estacao.codigo,
            data_hora_utc=previsao.obtido_em_utc,
            precipitacao_mm=None,
            temperatura_c=None,
            umidade_pct=previsao.umidade_solo_0_7cm,
            vento_velocidade_ms=previsao.vento_10m_kmh / 3.6,
            vento_rajada_ms=None,
            fonte_dados=FonteDados.AO_VIVO,
        )
        return "fallback"


async def _coordenadas_estacao(db: AsyncSession, estacao: EstacaoInmet) -> tuple[float, float]:
    """Extrai (lat, long) da geometria PostGIS da estação via ST_X/ST_Y."""
    from geoalchemy2.functions import ST_X, ST_Y

    resultado = await db.execute(select(ST_Y(estacao.posicao), ST_X(estacao.posicao)))
    latitude, longitude = resultado.one()
    return latitude, longitude


async def ingerir_todas_estacoes(db: AsyncSession) -> ResumoIngestao:
    """Job periódico (feature 002, US1/US2) — itera todas as estações do Pará."""
    resumo = ResumoIngestao()
    estacoes = (await db.execute(select(EstacaoInmet))).scalars().all()

    for estacao in estacoes:
        resultado = await ingerir_estacao(db, estacao)
        if resultado == "sucesso":
            resumo.estacoes_com_sucesso += 1
        elif resultado == "fallback":
            resumo.estacoes_com_fallback += 1
        else:
            resumo.estacoes_com_falha_total += 1

    await db.commit()
    return resumo
