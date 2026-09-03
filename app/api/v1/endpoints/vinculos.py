import uuid
from datetime import UTC, datetime
from typing import Annotated

from fastapi import APIRouter, Depends
from pydantic import BaseModel, EmailStr
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.response import AppError, envelope_sucesso
from app.core.security import Papel, UsuarioAutenticado, get_current_user, verificar_dono_ou_gestor
from app.db.models.usuario import Usuario
from app.db.models.vinculo_agronomo_propriedade import EstadoVinculo, VinculoAgronomoPropriedade
from app.db.session import get_db

router = APIRouter(tags=["vinculos"])


class VinculoCreate(BaseModel):
    agronomo_email: EmailStr


class VinculoRead(BaseModel):
    id: uuid.UUID
    agronomo_id: uuid.UUID
    propriedade_id: uuid.UUID
    estado: EstadoVinculo


def _serializar(vinculo: VinculoAgronomoPropriedade) -> VinculoRead:
    return VinculoRead(
        id=vinculo.id,
        agronomo_id=vinculo.agronomo_id,
        propriedade_id=vinculo.propriedade_id,
        estado=vinculo.estado,
    )


@router.post("/propriedades/{propriedade_id}/vinculos", status_code=201)
async def convidar_agronomo(
    propriedade_id: uuid.UUID,
    payload: VinculoCreate,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    """Feature 014/FR-005 — convite de vínculo agrônomo-propriedade; só o dono
    da propriedade ou GESTOR_TECNOLOGIA convida (mesma regra de escrita das
    demais rotas de propriedade)."""
    await verificar_dono_ou_gestor(db, usuario, propriedade_id)

    agronomo = (
        await db.execute(select(Usuario).where(Usuario.email == payload.agronomo_email))
    ).scalar_one_or_none()
    if agronomo is None or agronomo.papel != Papel.AGRONOMO:
        raise AppError(422, "E-mail não corresponde a um usuário com papel AGRONOMO.")

    vinculo = VinculoAgronomoPropriedade(agronomo_id=agronomo.id, propriedade_id=propriedade_id)
    db.add(vinculo)
    await db.commit()
    await db.refresh(vinculo)
    return envelope_sucesso(_serializar(vinculo).model_dump(mode="json"))


@router.post("/vinculos/{vinculo_id}/aceitar")
async def aceitar_vinculo(
    vinculo_id: uuid.UUID,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    """Feature 014/FR-005 — só o próprio agrônomo convidado aceita; um
    convite nunca vale sozinho (nunca é vínculo unilateral)."""
    vinculo = await db.get(VinculoAgronomoPropriedade, vinculo_id)
    if vinculo is None:
        raise AppError(404, "Convite não encontrado.")
    if vinculo.agronomo_id != usuario.id:
        raise AppError(403, "Este convite não é seu.")

    vinculo.estado = EstadoVinculo.ACEITO
    vinculo.aceito_em = datetime.now(UTC)
    await db.commit()
    await db.refresh(vinculo)
    return envelope_sucesso(_serializar(vinculo).model_dump(mode="json"))
