"""Consulta espacial nativa do PostGIS (Princípio IV — nunca haversine em Python)."""

from dataclasses import dataclass

from geoalchemy2.functions import ST_Centroid, ST_Distance
from geoalchemy2.types import Geography
from sqlalchemy import cast, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.estacao_inmet import EstacaoInmet
from app.db.models.talhao import Talhao

LIMITE_ESTACOES_IDW = 3  # calculos-geo-metero.md §1B (Escopo V3)


@dataclass
class EstacaoProximaResultado:
    estacao_codigo: str
    municipio: str
    distancia_km: float


async def buscar_estacoes_mais_proximas(
    db: AsyncSession, talhao: Talhao, limite: int = LIMITE_ESTACOES_IDW
) -> list[EstacaoProximaResultado]:
    """RF015/RF016/RF035/RNF003 — usa `<->` (KNN) sobre o centroide do talhão
    para explorar o índice GiST, e `::geography` para distância real em
    metros. Retorna até `limite` estações (padrão 3, para a interpolação IDW
    de `calculos-geo-metero.md` §1B); com menos estações cadastradas na área,
    retorna as disponíveis (mínimo 0)."""
    centroide = ST_Centroid(talhao.geometria)
    distancia_km = ST_Distance(cast(EstacaoInmet.posicao, Geography), cast(centroide, Geography)) / 1000

    resultado = await db.execute(
        select(EstacaoInmet.codigo, EstacaoInmet.nome, distancia_km)
        .order_by(EstacaoInmet.posicao.op("<->")(centroide))
        .limit(limite)
    )
    return [
        EstacaoProximaResultado(estacao_codigo=codigo, municipio=municipio, distancia_km=round(distancia, 2))
        for codigo, municipio, distancia in resultado.all()
    ]


async def buscar_estacao_mais_proxima(
    db: AsyncSession, talhao: Talhao
) -> EstacaoProximaResultado | None:
    """Estação única mais próxima — usada onde IDW não se aplica (ex.: RN007,
    precipitação diária "da estação INMET", singular, para o Balanço Hídrico)."""
    resultados = await buscar_estacoes_mais_proximas(db, talhao, limite=1)
    return resultados[0] if resultados else None
