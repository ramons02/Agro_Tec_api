"""Feature 008 — consulta de clima "do exato momento" (RN008, RN017, Princípio III).

Reaproveita a lógica de busca/fallback já implementada em `ingestao_service`
(feature 002), sem duplicá-la.
"""

import asyncio
import logging
from collections import defaultdict
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.calculos.pulverizacao import converter_ms_para_kmh
from app.db.models.estacao_inmet import EstacaoInmet
from app.db.models.medicao_clima import FonteDados, MedicaoClima
from app.db.models.talhao import Talhao
from app.db.queries.estacao_proxima import buscar_estacao_mais_proxima
from app.services.ingestao_service import ingerir_estacao

logger = logging.getLogger(__name__)

LIMITE_STALENESS = timedelta(minutes=30)  # RN008

# Lock por estação em memória (T001) — evita N buscas externas simultâneas
# quando várias requisições batem no mesmo talhão/estação com medição expirada
# ao mesmo tempo. Para múltiplas instâncias do backend, trocar por lock
# distribuído (Redis), conforme já previsto no research.md.
_locks_por_estacao: dict[str, asyncio.Lock] = defaultdict(asyncio.Lock)


@dataclass
class ClimaAtualResultado:
    estacao_codigo: str
    chuva_mm: float | None
    vento_kmh: float | None
    rajada_kmh: float | None
    fonte_dados: FonteDados
    medido_em_utc: datetime


async def _ultima_medicao(db: AsyncSession, estacao_codigo: str) -> MedicaoClima | None:
    resultado = await db.execute(
        select(MedicaoClima)
        .where(MedicaoClima.estacao_codigo == estacao_codigo)
        .order_by(MedicaoClima.data_hora_utc.desc())
        .limit(1)
    )
    return resultado.scalars().first()


def _esta_expirada(medicao: MedicaoClima | None) -> bool:
    if medicao is None:
        return True
    return datetime.now(UTC) - medicao.data_hora_utc > LIMITE_STALENESS


def _para_resultado(medicao: MedicaoClima, fonte_dados: FonteDados) -> ClimaAtualResultado:
    return ClimaAtualResultado(
        estacao_codigo=medicao.estacao_codigo,
        chuva_mm=float(medicao.precipitacao_mm) if medicao.precipitacao_mm is not None else None,
        vento_kmh=(
            converter_ms_para_kmh(float(medicao.vento_velocidade_ms))
            if medicao.vento_velocidade_ms is not None
            else None
        ),
        rajada_kmh=(
            converter_ms_para_kmh(float(medicao.vento_rajada_ms))
            if medicao.vento_rajada_ms is not None
            else None
        ),
        fonte_dados=fonte_dados,
        medido_em_utc=medicao.data_hora_utc,
    )


async def obter_clima_atual(db: AsyncSession, talhao: Talhao) -> ClimaAtualResultado | None:
    """RN008/RN017 — nunca retorna dado >30min sem antes tentar atualizar; se
    todas as fontes falharem, retorna a última medição válida sinalizada como
    CACHE_EXPIRADO. Retorna None só se não houver NENHUMA medição histórica."""
    resultado_estacao = await buscar_estacao_mais_proxima(db, talhao)
    if resultado_estacao is None:
        return None

    estacao_codigo = resultado_estacao.estacao_codigo
    medicao = await _ultima_medicao(db, estacao_codigo)

    if not _esta_expirada(medicao):
        return _para_resultado(medicao, FonteDados.AO_VIVO)

    async with _locks_por_estacao[estacao_codigo]:
        # Double-check: outra requisição pode já ter atualizado enquanto esperava o lock.
        medicao = await _ultima_medicao(db, estacao_codigo)
        if not _esta_expirada(medicao):
            return _para_resultado(medicao, FonteDados.AO_VIVO)

        estacao = await db.get(EstacaoInmet, estacao_codigo)
        resultado_busca = await ingerir_estacao(db, estacao)
        await db.commit()

        medicao_atualizada = await _ultima_medicao(db, estacao_codigo)
        if resultado_busca in ("sucesso", "fallback") and medicao_atualizada is not None:
            return _para_resultado(medicao_atualizada, FonteDados.AO_VIVO)

        # RN017 — todas as fontes falharam: última medição válida, nunca um erro.
        if medicao_atualizada is not None:
            return _para_resultado(medicao_atualizada, FonteDados.CACHE_EXPIRADO)

        return None
