import json
from typing import Annotated

from fastapi import APIRouter, Depends
from geoalchemy2.functions import ST_AsGeoJSON
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import aliased

from app.core.calculos.pulverizacao import converter_ms_para_kmh
from app.core.response import envelope_sucesso
from app.core.security import UsuarioAutenticado, get_current_user, propriedade_ids_visiveis
from app.db.models.balanco_hidrico_diario import BalancoHidricoDiario
from app.db.models.estacao_inmet import EstacaoInmet
from app.db.models.medicao_clima import MedicaoClima
from app.db.models.propriedade import Propriedade
from app.db.models.talhao import Talhao
from app.db.session import get_db

router = APIRouter(prefix="/mapa", tags=["mapa"])


def _subquery_ultimo_status_plantio():
    """Mesma janela usada pelo dashboard (feature 011) — status_plantio mais
    recente por talhão."""
    linha_mais_recente = select(
        BalancoHidricoDiario.talhao_id,
        func.row_number()
        .over(partition_by=BalancoHidricoDiario.talhao_id, order_by=BalancoHidricoDiario.data.desc())
        .label("rn"),
        BalancoHidricoDiario.status_plantio,
    ).subquery()
    return select(linha_mais_recente).where(linha_mais_recente.c.rn == 1).subquery()


def _subquery_ultima_medicao():
    """Última `MedicaoClima` por estação (feature 002/008)."""
    linha_mais_recente = select(
        MedicaoClima.estacao_codigo,
        func.row_number()
        .over(partition_by=MedicaoClima.estacao_codigo, order_by=MedicaoClima.data_hora_utc.desc())
        .label("rn"),
        MedicaoClima.precipitacao_mm,
        MedicaoClima.vento_velocidade_ms,
        MedicaoClima.fonte_dados,
    ).subquery()
    return select(linha_mais_recente).where(linha_mais_recente.c.rn == 1).subquery()


@router.get("/dados")
async def obter_dados_mapa(
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    """FR-001-FR-003 (feature 007) — payload agregado para o mapa: geometrias
    de propriedades/talhões (com status de plantio) e estações (com última
    medição), tudo já em GeoJSON. Feature 014/FR-004: só propriedades
    visíveis ao usuário; estações são infraestrutura pública, sem RBAC."""
    ids_visiveis = await propriedade_ids_visiveis(db, usuario)

    query_propriedades = select(Propriedade)
    if ids_visiveis is not None:
        query_propriedades = query_propriedades.where(Propriedade.id.in_(ids_visiveis))
    propriedades = (await db.execute(query_propriedades)).scalars().all()

    ultimo_status = aliased(_subquery_ultimo_status_plantio(), name="ultimo_status")
    query_talhoes = select(
        Talhao.id,
        Talhao.propriedade_id,
        Talhao.nome,
        ST_AsGeoJSON(Talhao.geometria).label("geometria_geojson"),
        ultimo_status.c.status_plantio,
    ).outerjoin(ultimo_status, ultimo_status.c.talhao_id == Talhao.id)
    if ids_visiveis is not None:
        query_talhoes = query_talhoes.where(Talhao.propriedade_id.in_(ids_visiveis))
    talhoes_por_propriedade: dict = {}
    for linha in (await db.execute(query_talhoes)).all():
        talhoes_por_propriedade.setdefault(linha.propriedade_id, []).append(
            {
                "id": str(linha.id),
                "nome": linha.nome,
                "geometria_geojson": json.loads(linha.geometria_geojson),
                "status_plantio": linha.status_plantio.value if linha.status_plantio else None,
            }
        )

    ultima_medicao = aliased(_subquery_ultima_medicao(), name="ultima_medicao")
    query_estacoes = select(
        EstacaoInmet.codigo,
        EstacaoInmet.nome,
        ST_AsGeoJSON(EstacaoInmet.posicao).label("posicao_geojson"),
        ultima_medicao.c.precipitacao_mm,
        ultima_medicao.c.vento_velocidade_ms,
        ultima_medicao.c.fonte_dados,
    ).outerjoin(ultima_medicao, ultima_medicao.c.estacao_codigo == EstacaoInmet.codigo)
    estacoes = []
    for linha in (await db.execute(query_estacoes)).all():
        tem_medicao = linha.precipitacao_mm is not None or linha.vento_velocidade_ms is not None
        estacoes.append(
            {
                "codigo": linha.codigo,
                "municipio": linha.nome,
                "posicao_geojson": json.loads(linha.posicao_geojson),
                "ultima_medicao": (
                    {
                        "chuva_mm": (
                            float(linha.precipitacao_mm) if linha.precipitacao_mm is not None else None
                        ),
                        "vento_kmh": (
                            round(converter_ms_para_kmh(float(linha.vento_velocidade_ms)), 1)
                            if linha.vento_velocidade_ms is not None
                            else None
                        ),
                        "fonte_dados": linha.fonte_dados.value if linha.fonte_dados else None,
                    }
                    if tem_medicao
                    else None
                ),
            }
        )

    return envelope_sucesso(
        {
            "propriedades": [
                {
                    "id": str(propriedade.id),
                    "nome": propriedade.nome,
                    "talhoes": talhoes_por_propriedade.get(propriedade.id, []),
                }
                for propriedade in propriedades
            ],
            "estacoes": estacoes,
        }
    )
