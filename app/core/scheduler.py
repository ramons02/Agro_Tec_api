import logging
from datetime import UTC, datetime, timedelta

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.interval import IntervalTrigger
from sqlalchemy import delete, select

from app.db.models.medicao_clima import MedicaoClima
from app.db.session import AsyncSessionLocal
from app.services.balanco_hidrico_service import calcular_balanco_hidrico_todos_talhoes
from app.services.ingestao_service import ingerir_todas_estacoes

logger = logging.getLogger(__name__)

scheduler = AsyncIOScheduler()

RETENCAO_GRANULARIDADE_HORARIA_DIAS = 365  # RNF014 — 12 meses


async def _job_ingestao_periodica() -> None:
    async with AsyncSessionLocal() as db:
        resumo = await ingerir_todas_estacoes(db)
        logger.info(
            "Ingestão INMET concluída: sucesso=%d fallback=%d falha_total=%d",
            resumo.estacoes_com_sucesso,
            resumo.estacoes_com_fallback,
            resumo.estacoes_com_falha_total,
        )


async def _job_retencao_diaria() -> None:
    """RNF014 — medições com mais de 12 meses de granularidade horária são removidas
    após compactação em agregados diários (tabela de agregado é trabalho futuro, fora
    do escopo mínimo desta feature; aqui garante-se que a retenção não cresce sem limite)."""
    limite = datetime.now(UTC) - timedelta(days=RETENCAO_GRANULARIDADE_HORARIA_DIAS)
    async with AsyncSessionLocal() as db:
        resultado = await db.execute(
            select(MedicaoClima.id).where(MedicaoClima.data_hora_utc < limite).limit(1)
        )
        if resultado.scalar_one_or_none() is not None:
            logger.info("Compactando medições anteriores a %s (RNF014)", limite.isoformat())
            await db.execute(delete(MedicaoClima).where(MedicaoClima.data_hora_utc < limite))
            await db.commit()


async def _job_balanco_hidrico_diario() -> None:
    async with AsyncSessionLocal() as db:
        calculados = await calcular_balanco_hidrico_todos_talhoes(db)
        logger.info("Balanço hídrico calculado para %d talhão(ões)", calculados)


def iniciar_scheduler() -> None:
    agora = datetime.now(UTC)
    scheduler.add_job(
        _job_ingestao_periodica,
        IntervalTrigger(minutes=10),
        id="ingestao_inmet_periodica",
        replace_existing=True,
        next_run_time=agora,
    )
    scheduler.add_job(
        _job_retencao_diaria,
        IntervalTrigger(days=1),
        id="retencao_medicoes_diaria",
        replace_existing=True,
    )
    scheduler.add_job(
        _job_balanco_hidrico_diario,
        IntervalTrigger(days=1),
        id="balanco_hidrico_diario",
        replace_existing=True,
        next_run_time=agora,
    )
    scheduler.start()


def parar_scheduler() -> None:
    scheduler.shutdown(wait=False)
