import uuid
from typing import Annotated, Any

from fastapi import APIRouter, Depends
from geoalchemy2.functions import ST_AsGeoJSON
from geoalchemy2.shape import from_shape
from pydantic import BaseModel
from shapely.geometry import shape
from sqlalchemy import delete, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.response import AppError, envelope_sucesso
from app.core.security import (
    ERRO_AGRONOMO_SOMENTE_LEITURA,
    Papel,
    UsuarioAutenticado,
    get_current_user,
    propriedade_ids_visiveis,
    verificar_dono_ou_gestor,
)
from app.db.models.propriedade import Propriedade
from app.db.session import get_db
from app.services.importacao_geo_service import normalizar_para_multipolygon

router = APIRouter(prefix="/propriedades", tags=["propriedades"])

DEFAULT_PAGE_SIZE = 20


class PropriedadeCreate(BaseModel):
    nome: str
    municipio: str | None = None  # obrigatorio na UI (busca por cidade), opcional na API
    geometria: dict[str, Any] | None = None  # GeoJSON Polygon ou MultiPolygon, opcional (RD001)


class PropriedadeRead(BaseModel):
    id: uuid.UUID
    nome: str
    municipio: str | None
    proprietario_id: uuid.UUID
    geometria: dict[str, Any] | None


async def _serializar(db: AsyncSession, propriedade: Propriedade) -> PropriedadeRead:
    geometria_geojson = None
    if propriedade.geometria is not None:
        resultado = await db.execute(select(ST_AsGeoJSON(propriedade.geometria)))
        import json

        geometria_geojson = json.loads(resultado.scalar_one())
    return PropriedadeRead(
        id=propriedade.id,
        nome=propriedade.nome,
        municipio=propriedade.municipio,
        proprietario_id=propriedade.proprietario_id,
        geometria=geometria_geojson,
    )


@router.post("")
async def criar_propriedade(
    payload: PropriedadeCreate,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    """RF001 (feature 005) — FR-001: criar propriedade. Feature 014/FR-004:
    AGRONOMO é sempre somente leitura, nunca cria propriedade própria."""
    if usuario.papel == Papel.AGRONOMO:
        raise AppError(403, ERRO_AGRONOMO_SOMENTE_LEITURA, {"papel": usuario.papel.value})

    geometria = (
        from_shape(normalizar_para_multipolygon(shape(payload.geometria)), srid=4326)
        if payload.geometria
        else None
    )
    propriedade = Propriedade(
        nome=payload.nome,
        municipio=payload.municipio,
        proprietario_id=usuario.id,
        geometria=geometria,
    )
    db.add(propriedade)
    await db.commit()
    await db.refresh(propriedade)
    return envelope_sucesso((await _serializar(db, propriedade)).model_dump(mode="json"))


@router.get("")
async def listar_propriedades(
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
    page: int = 1,
    page_size: int = DEFAULT_PAGE_SIZE,
) -> dict:
    """FR-009/RNF017 — paginação (20/página por padrão). Feature 014/FR-004:
    PRODUTOR_RURAL só vê as próprias; AGRONOMO só as vinculadas e aceitas;
    GESTOR_TECNOLOGIA vê todas."""
    ids_visiveis = await propriedade_ids_visiveis(db, usuario)

    query = select(Propriedade)
    contagem = select(func.count()).select_from(Propriedade)
    if ids_visiveis is not None:
        query = query.where(Propriedade.id.in_(ids_visiveis))
        contagem = contagem.where(Propriedade.id.in_(ids_visiveis))

    total = (await db.execute(contagem)).scalar_one()
    resultado = await db.execute(query.offset((page - 1) * page_size).limit(page_size))
    propriedades = resultado.scalars().all()
    itens = [(await _serializar(db, p)).model_dump(mode="json") for p in propriedades]
    return envelope_sucesso({"itens": itens, "total": total, "page": page, "page_size": page_size})


async def _buscar_propriedade_ou_404(db: AsyncSession, propriedade_id: uuid.UUID) -> Propriedade:
    propriedade = await db.get(Propriedade, propriedade_id)
    if propriedade is None:
        raise AppError(404, "Propriedade não encontrada.")
    return propriedade


async def _verificar_visivel_ou_404(
    db: AsyncSession, usuario: UsuarioAutenticado, propriedade_id: uuid.UUID
) -> None:
    """Feature 014/FR-004 — leitura de um recurso específico (não listagem):
    404, não 403, porque aqui não há ação bloqueada, só um recurso que este
    usuário não pode ver (evita confirmar que o id existe para quem não tem
    acesso a ele)."""
    ids_visiveis = await propriedade_ids_visiveis(db, usuario)
    if ids_visiveis is not None and propriedade_id not in ids_visiveis:
        raise AppError(404, "Propriedade não encontrada.")


@router.get("/{propriedade_id}")
async def obter_propriedade(
    propriedade_id: uuid.UUID,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    await _verificar_visivel_ou_404(db, usuario, propriedade_id)
    propriedade = await _buscar_propriedade_ou_404(db, propriedade_id)
    return envelope_sucesso((await _serializar(db, propriedade)).model_dump(mode="json"))


@router.put("/{propriedade_id}")
async def atualizar_propriedade(
    propriedade_id: uuid.UUID,
    payload: PropriedadeCreate,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    await verificar_dono_ou_gestor(db, usuario, propriedade_id)
    propriedade = await _buscar_propriedade_ou_404(db, propriedade_id)
    propriedade.nome = payload.nome
    propriedade.municipio = payload.municipio
    if payload.geometria is not None:
        propriedade.geometria = from_shape(
            normalizar_para_multipolygon(shape(payload.geometria)), srid=4326
        )
    await db.commit()
    await db.refresh(propriedade)
    return envelope_sucesso((await _serializar(db, propriedade)).model_dump(mode="json"))


@router.delete("/{propriedade_id}", status_code=204)
async def excluir_propriedade(
    propriedade_id: uuid.UUID,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> None:
    """FR-003 — exclui a propriedade; talhões vinculados são removidos em cascata
    pelo `ON DELETE CASCADE` da FK (nível de banco, não aplicação)."""
    await verificar_dono_ou_gestor(db, usuario, propriedade_id)
    await _buscar_propriedade_ou_404(db, propriedade_id)
    await db.execute(delete(Propriedade).where(Propriedade.id == propriedade_id))
    await db.commit()
