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
from app.core.security import UsuarioAutenticado, get_current_user
from app.db.models.propriedade import Propriedade
from app.db.session import get_db

router = APIRouter(prefix="/propriedades", tags=["propriedades"])

DEFAULT_PAGE_SIZE = 20


class PropriedadeCreate(BaseModel):
    nome: str
    geometria: dict[str, Any] | None = None  # GeoJSON Polygon, opcional (RD001)


class PropriedadeRead(BaseModel):
    id: uuid.UUID
    nome: str
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
        proprietario_id=propriedade.proprietario_id,
        geometria=geometria_geojson,
    )


@router.post("")
async def criar_propriedade(
    payload: PropriedadeCreate,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    """RF001 (feature 005) — FR-001: criar propriedade."""
    geometria = from_shape(shape(payload.geometria), srid=4326) if payload.geometria else None
    propriedade = Propriedade(nome=payload.nome, proprietario_id=usuario.id, geometria=geometria)
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
    """FR-009/RNF017 — paginação (20/página por padrão)."""
    total = (await db.execute(select(func.count()).select_from(Propriedade))).scalar_one()
    resultado = await db.execute(
        select(Propriedade).offset((page - 1) * page_size).limit(page_size)
    )
    propriedades = resultado.scalars().all()
    itens = [(await _serializar(db, p)).model_dump(mode="json") for p in propriedades]
    return envelope_sucesso({"itens": itens, "total": total, "page": page, "page_size": page_size})


async def _buscar_propriedade_ou_404(db: AsyncSession, propriedade_id: uuid.UUID) -> Propriedade:
    propriedade = await db.get(Propriedade, propriedade_id)
    if propriedade is None:
        raise AppError(404, "Propriedade não encontrada.")
    return propriedade


@router.get("/{propriedade_id}")
async def obter_propriedade(
    propriedade_id: uuid.UUID,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    propriedade = await _buscar_propriedade_ou_404(db, propriedade_id)
    return envelope_sucesso((await _serializar(db, propriedade)).model_dump(mode="json"))


@router.put("/{propriedade_id}")
async def atualizar_propriedade(
    propriedade_id: uuid.UUID,
    payload: PropriedadeCreate,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    propriedade = await _buscar_propriedade_ou_404(db, propriedade_id)
    propriedade.nome = payload.nome
    if payload.geometria is not None:
        propriedade.geometria = from_shape(shape(payload.geometria), srid=4326)
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
    await _buscar_propriedade_ou_404(db, propriedade_id)
    await db.execute(delete(Propriedade).where(Propriedade.id == propriedade_id))
    await db.commit()
