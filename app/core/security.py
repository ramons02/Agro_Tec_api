import asyncio
from datetime import UTC, datetime, timedelta
from typing import Annotated
from uuid import UUID

from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError, jwt
from passlib.context import CryptContext
from pydantic import BaseModel

from app.core.config import get_settings
from app.core.response import AppError
from app.db.models.usuario import Papel

settings = get_settings()
_pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
_bearer_scheme = HTTPBearer(auto_error=False)

ERRO_TOKEN_INVALIDO = "Token de autorização ausente ou expirado."


class UsuarioAutenticado(BaseModel):
    id: UUID
    papel: Papel


async def hash_senha(senha: str) -> str:
    """bcrypt é CPU-bound e lento de propósito — nunca rodar direto no event loop (research.md)."""
    return await asyncio.to_thread(_pwd_context.hash, senha)


async def verificar_senha(senha_plana: str, senha_hash: str) -> bool:
    return await asyncio.to_thread(_pwd_context.verify, senha_plana, senha_hash)


def criar_token(usuario_id: UUID, papel: Papel) -> tuple[str, datetime]:
    expira_em = datetime.now(UTC) + timedelta(hours=settings.jwt_expiration_hours)
    claims = {"sub": str(usuario_id), "papel": papel.value, "exp": expira_em}
    token = jwt.encode(claims, settings.jwt_secret, algorithm=settings.jwt_algorithm)
    return token, expira_em


def _decodificar_token(token: str) -> UsuarioAutenticado:
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
        return UsuarioAutenticado(id=UUID(payload["sub"]), papel=Papel(payload["papel"]))
    except (JWTError, KeyError, ValueError) as exc:
        raise AppError(401, ERRO_TOKEN_INVALIDO) from exc


async def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer_scheme)],
) -> UsuarioAutenticado:
    if credentials is None:
        raise AppError(401, ERRO_TOKEN_INVALIDO)
    return _decodificar_token(credentials.credentials)
