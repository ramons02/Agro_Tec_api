"""Feature 008 — consulta de clima "do exato momento" (RN008, RN017, Princípio III).

Escopo V3 (2026-09-03): o valor usado passa a ser a interpolação IDW entre as
3 estações mais próximas (`calculos-geo-metero.md` §1B), não mais só a
estação mais próxima. Reaproveita a lógica de busca/fallback já implementada
em `ingestao_service` (feature 002), aplicada a cada uma das 3 estações.
"""

import asyncio
import logging
from collections import defaultdict
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.calculos.idw import interpolar_idw
from app.core.calculos.pulverizacao import converter_ms_para_kmh
from app.db.models.estacao_inmet import EstacaoInmet
from app.db.models.medicao_clima import FonteDados, MedicaoClima
from app.db.models.talhao import Talhao
from app.db.queries.estacao_proxima import buscar_estacoes_mais_proximas
from app.services.ingestao_service import ingerir_estacao

logger = logging.getLogger(__name__)

LIMITE_STALENESS = timedelta(minutes=30)  # RN008

# Lock por estação em memória — evita N buscas externas simultâneas quando
# várias requisições batem na mesma estação expirada ao mesmo tempo. Para
# múltiplas instâncias do backend, trocar por lock distribuído (Redis).
_locks_por_estacao: dict[str, asyncio.Lock] = defaultdict(asyncio.Lock)


@dataclass
class ClimaAtualResultado:
    estacao_codigo: str
    """Código da estação mais próxima (referência) — o valor abaixo é a
    interpolação IDW entre até 3 estações, não necessariamente só desta."""
    chuva_mm: float | None
    vento_kmh: float | None
    rajada_kmh: float | None
    temperatura_c: float | None
    umidade_pct: float | None
    fonte_dados: FonteDados
    medido_em_utc: datetime


@dataclass
class _LeituraEstacao:
    distancia_km: float
    medicao: MedicaoClima
    fonte_dados: FonteDados


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


async def _obter_leitura_fresca(
    db: AsyncSession, estacao_codigo: str
) -> tuple[MedicaoClima, FonteDados] | None:
    """RN008/RN017 por estação: busca/atualiza se expirada; retorna a última
    medição válida (mesmo expirada) em vez de None sempre que possível."""
    medicao = await _ultima_medicao(db, estacao_codigo)
    if not _esta_expirada(medicao):
        return medicao, FonteDados.AO_VIVO

    async with _locks_por_estacao[estacao_codigo]:
        medicao = await _ultima_medicao(db, estacao_codigo)
        if not _esta_expirada(medicao):
            return medicao, FonteDados.AO_VIVO

        estacao = await db.get(EstacaoInmet, estacao_codigo)
        resultado_busca = await ingerir_estacao(db, estacao)
        await db.commit()

        medicao_atualizada = await _ultima_medicao(db, estacao_codigo)
        if resultado_busca in ("sucesso", "fallback") and medicao_atualizada is not None:
            return medicao_atualizada, FonteDados.AO_VIVO

        if medicao_atualizada is not None:
            return medicao_atualizada, FonteDados.CACHE_EXPIRADO

        return None


def _interpolar_campo(leituras: list[_LeituraEstacao], campo: str) -> float | None:
    valores_e_distancias = [
        (float(getattr(leitura.medicao, campo)), leitura.distancia_km)
        for leitura in leituras
        if getattr(leitura.medicao, campo) is not None
    ]
    if not valores_e_distancias:
        return None
    return interpolar_idw(valores_e_distancias)


async def obter_clima_atual(db: AsyncSession, talhao: Talhao) -> ClimaAtualResultado | None:
    """RN008/RN017/RF035 — nunca retorna dado >30min sem antes tentar
    atualizar; se todas as fontes falharem, retorna a última medição válida
    sinalizada como CACHE_EXPIRADO. Retorna None só se não houver NENHUMA
    medição histórica em NENHUMA das estações próximas."""
    estacoes = await buscar_estacoes_mais_proximas(db, talhao)
    if not estacoes:
        return None

    leituras: list[_LeituraEstacao] = []
    for estacao in estacoes:
        leitura = await _obter_leitura_fresca(db, estacao.estacao_codigo)
        if leitura is not None:
            medicao, fonte = leitura
            leituras.append(_LeituraEstacao(estacao.distancia_km, medicao, fonte))

    if not leituras:
        return None

    fonte_dados = (
        FonteDados.AO_VIVO
        if all(leitura.fonte_dados == FonteDados.AO_VIVO for leitura in leituras)
        else FonteDados.CACHE_EXPIRADO
    )
    # Timestamp mais conservador (mais antigo) entre as leituras que compõem o resultado.
    medido_em_utc = min(leitura.medicao.data_hora_utc for leitura in leituras)

    vento_ms = _interpolar_campo(leituras, "vento_velocidade_ms")
    rajada_ms = _interpolar_campo(leituras, "vento_rajada_ms")

    return ClimaAtualResultado(
        estacao_codigo=estacoes[0].estacao_codigo,
        chuva_mm=_interpolar_campo(leituras, "precipitacao_mm"),
        vento_kmh=converter_ms_para_kmh(vento_ms) if vento_ms is not None else None,
        rajada_kmh=converter_ms_para_kmh(rajada_ms) if rajada_ms is not None else None,
        temperatura_c=_interpolar_campo(leituras, "temperatura_c"),
        umidade_pct=_interpolar_campo(leituras, "umidade_pct"),
        fonte_dados=fonte_dados,
        medido_em_utc=medido_em_utc,
    )
