from typing import Annotated

from fastapi import APIRouter, Depends
from pydantic import BaseModel, EmailStr
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.response import AppError, envelope_sucesso
from app.core.security import UsuarioAutenticado, criar_token, get_current_user, verificar_senha
from app.db.models.usuario import Usuario
from app.db.session import get_db

router = APIRouter(prefix="/auth", tags=["auth"])

MENSAGEM_CREDENCIAIS_INVALIDAS = "Usuário ou senha inválidos."


class LoginRequest(BaseModel):
    email: EmailStr
    senha: str


@router.post("/login")
async def login(
    payload: LoginRequest,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    """RF001/RF002 — valida credenciais e emite token JWT de 24h (HU-01)."""
    resultado = await db.execute(select(Usuario).where(Usuario.email == payload.email))
    usuario = resultado.scalar_one_or_none()

    # Mensagem genérica em ambos os ramos: nunca revelar se o email existe (spec 001, US1).
    if usuario is None:
        raise AppError(401, MENSAGEM_CREDENCIAIS_INVALIDAS)
    if not await verificar_senha(payload.senha, usuario.senha_hash):
        raise AppError(401, MENSAGEM_CREDENCIAIS_INVALIDAS)

    token, expira_em = criar_token(usuario.id, usuario.papel)
    return envelope_sucesso(
        {
            "token": token,
            "expira_em": expira_em.isoformat(),
            "papel": usuario.papel.value,
        }
    )


@router.get("/me")
async def me(usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)]) -> dict:
    """Rota mínima para validar a dependência get_current_user isoladamente (tasks.md T017)."""
    return envelope_sucesso({"id": str(usuario.id), "papel": usuario.papel.value})
