"""Popula/atualiza `estacoes_inmet` a partir do catálogo real do INMET (tasks.md T005).

Buscar dinamicamente em vez de hardcodar a lista: o conjunto de estações
automáticas do INMET no Pará muda ao longo do tempo (novas estações entram,
outras saem de operação) — uma lista fixa ficaria desatualizada. Rodar via:

    python -m app.scripts.seed_estacoes_inmet

Requer acesso de rede à apitempo.inmet.gov.br e `DATABASE_URL` configurado.
"""

import asyncio
import logging

from geoalchemy2.functions import ST_SetSRID
from sqlalchemy import func
from sqlalchemy.dialects.postgresql import insert as pg_insert

from app.db.models.estacao_inmet import EstacaoInmet
from app.db.session import AsyncSessionLocal
from app.services.inmet_service import buscar_estacoes_pa

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


async def seed() -> None:
    estacoes = await buscar_estacoes_pa()
    if not estacoes:
        logger.warning("Nenhuma estação do Pará retornada pelo catálogo do INMET.")
        return

    async with AsyncSessionLocal() as db:
        for estacao in estacoes:
            ponto = ST_SetSRID(func.ST_MakePoint(estacao.longitude, estacao.latitude), 4326)
            stmt = (
                pg_insert(EstacaoInmet)
                .values(codigo=estacao.codigo, nome=estacao.nome, estado="PA", posicao=ponto)
                .on_conflict_do_update(
                    index_elements=["codigo"],
                    set_={"nome": estacao.nome, "posicao": ponto},
                )
            )
            await db.execute(stmt)
        await db.commit()

    logger.info("%d estações do Pará sincronizadas.", len(estacoes))


if __name__ == "__main__":
    asyncio.run(seed())
