import uuid
from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import aliased

from app.core.calculos.status_plantio import StatusPlantio
from app.core.response import envelope_sucesso
from app.core.security import UsuarioAutenticado, get_current_user, propriedade_ids_visiveis
from app.db.models.balanco_hidrico_diario import BalancoHidricoDiario
from app.db.models.propriedade import Propriedade
from app.db.models.talhao import Talhao
from app.db.session import get_db

router = APIRouter(prefix="/dashboard", tags=["dashboard"])

DEFAULT_PAGE_SIZE = 20


def _subquery_ultimo_balanco():
    """Registro mais recente de BalancoHidricoDiario por talhão (RF026)."""
    linha_mais_recente = (
        select(
            BalancoHidricoDiario.talhao_id,
            func.row_number()
            .over(
                partition_by=BalancoHidricoDiario.talhao_id,
                order_by=BalancoHidricoDiario.data.desc(),
            )
            .label("rn"),
            BalancoHidricoDiario.armazenamento_mm,
            BalancoHidricoDiario.status_plantio,
        )
        .subquery()
    )
    return select(linha_mais_recente).where(linha_mais_recente.c.rn == 1).subquery()


@router.get("/plantio")
async def dashboard_plantio(
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
    propriedade_id: uuid.UUID | None = None,
    status: StatusPlantio | None = None,
    page: int = 1,
    page_size: int = DEFAULT_PAGE_SIZE,
) -> dict:
    """RF026/RF027, RNF017 (feature 011) — feature 014/FR-004: escopo por
    propriedades visíveis ao usuário (dono, vínculo aceito, ou GESTOR_TECNOLOGIA
    sem restrição)."""
    ids_visiveis = await propriedade_ids_visiveis(db, usuario)
    ultimo_balanco = aliased(_subquery_ultimo_balanco(), name="ultimo_balanco")

    query = (
        select(
            Talhao.id,
            Talhao.nome,
            Propriedade.nome.label("propriedade_nome"),
            Talhao.area_ha,
            Talhao.tipo_solo,
            ultimo_balanco.c.status_plantio,
            ultimo_balanco.c.armazenamento_mm,
            Talhao.capacidade_agua_disponivel_mm,
        )
        .join(Propriedade, Propriedade.id == Talhao.propriedade_id)
        .outerjoin(ultimo_balanco, ultimo_balanco.c.talhao_id == Talhao.id)
    )
    if ids_visiveis is not None:
        query = query.where(Talhao.propriedade_id.in_(ids_visiveis))
    if propriedade_id is not None:
        query = query.where(Talhao.propriedade_id == propriedade_id)
    if status is not None:
        query = query.where(ultimo_balanco.c.status_plantio == status)

    total = (await db.execute(select(func.count()).select_from(query.subquery()))).scalar_one()
    resultado = await db.execute(query.offset((page - 1) * page_size).limit(page_size))

    itens = []
    for linha in resultado.all():
        cad = float(linha.capacidade_agua_disponivel_mm) if linha.capacidade_agua_disponivel_mm else None
        armazenamento = float(linha.armazenamento_mm) if linha.armazenamento_mm is not None else None
        percentual_cad = (armazenamento / cad * 100) if (cad and armazenamento is not None) else None
        itens.append(
            {
                "talhao_id": str(linha.id),
                "nome": linha.nome,
                "propriedade": linha.propriedade_nome,
                "area_ha": float(linha.area_ha),
                "tipo_solo": linha.tipo_solo.value if linha.tipo_solo else None,
                "status_plantio": linha.status_plantio.value if linha.status_plantio else None,
                "armazenamento_mm": armazenamento,
                "percentual_cad": round(percentual_cad, 1) if percentual_cad is not None else None,
            }
        )

    return envelope_sucesso({"itens": itens, "total": total, "page": page, "page_size": page_size})
