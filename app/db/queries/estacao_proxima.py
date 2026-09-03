"""Consulta espacial nativa do PostGIS (Princípio IV — nunca haversine em Python)."""

from dataclasses import dataclass

from geoalchemy2.functions import ST_Centroid, ST_Distance
from geoalchemy2.types import Geography
from sqlalchemy import cast, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.estacao_inmet import EstacaoInmet
from app.db.models.talhao import Talhao


@dataclass
class EstacaoProximaResultado:
    estacao_codigo: str
    municipio: str
    distancia_km: float


async def buscar_estacao_mais_proxima(
    db: AsyncSession, talhao: Talhao
) -> EstacaoProximaResultado | None:
    """RF015/RF016/RNF003 — usa `<->` (KNN) sobre o centroide do talhão para
    explorar o índice GiST, e `::geography` para distância real em metros."""
    centroide = ST_Centroid(talhao.geometria)
    distancia_km = ST_Distance(cast(EstacaoInmet.posicao, Geography), cast(centroide, Geography)) / 1000

    resultado = await db.execute(
        select(EstacaoInmet.codigo, EstacaoInmet.nome, distancia_km)
        .order_by(EstacaoInmet.posicao.op("<->")(centroide))
        .limit(1)
    )
    linha = resultado.first()
    if linha is None:
        return None

    codigo, municipio, distancia = linha
    return EstacaoProximaResultado(
        estacao_codigo=codigo, municipio=municipio, distancia_km=round(distancia, 2)
    )
