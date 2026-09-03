import asyncio
from datetime import UTC, datetime, timedelta
from typing import Annotated
from uuid import UUID

from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError, jwt
from passlib.context import CryptContext
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.response import AppError
from app.db.models.propriedade import Propriedade
from app.db.models.usuario import Papel
from app.db.models.vinculo_agronomo_propriedade import EstadoVinculo, VinculoAgronomoPropriedade

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


ERRO_SEM_PERMISSAO_ESCRITA = "Você não tem permissão para alterar esta propriedade."
ERRO_AGRONOMO_SOMENTE_LEITURA = "Agrônomos têm acesso somente leitura."


async def verificar_dono_ou_gestor(
    db: AsyncSession, usuario: UsuarioAutenticado, propriedade_id: UUID
) -> None:
    """Feature 014 (FR-002/FR-003/FR-004/FR-006) — só o dono cadastrado
    (`PRODUTOR_RURAL`) ou `GESTOR_TECNOLOGIA` podem escrever numa propriedade
    (e, por extensão, nos talhões dela). `AGRONOMO` nunca escreve, mesmo com
    vínculo aceito (FR-004). Erro é sempre 403 explícito, nunca um 404
    disfarçado (FR-006) — a checagem de permissão não deve revelar, nem
    esconder, se o recurso existe."""
    if usuario.papel == Papel.GESTOR_TECNOLOGIA:
        return
    if usuario.papel == Papel.AGRONOMO:
        raise AppError(403, ERRO_AGRONOMO_SOMENTE_LEITURA, {"papel": usuario.papel.value})

    propriedade = await db.get(Propriedade, propriedade_id)
    if propriedade is not None and propriedade.proprietario_id == usuario.id:
        return
    raise AppError(403, ERRO_SEM_PERMISSAO_ESCRITA, {"papel": usuario.papel.value})


async def propriedade_ids_visiveis(db: AsyncSession, usuario: UsuarioAutenticado) -> list[UUID] | None:
    """Feature 014 (FR-002/FR-003/FR-004) — ids de propriedade que `usuario`
    pode enxergar em listagens. `None` significa "sem restrição" (só
    `GESTOR_TECNOLOGIA`); para os outros papéis, sempre uma lista (mesmo que
    vazia) — nunca `None`, para não ser confundido com "sem restrição"."""
    if usuario.papel == Papel.GESTOR_TECNOLOGIA:
        return None

    if usuario.papel == Papel.PRODUTOR_RURAL:
        resultado = await db.execute(
            select(Propriedade.id).where(Propriedade.proprietario_id == usuario.id)
        )
        return list(resultado.scalars().all())

    resultado = await db.execute(
        select(VinculoAgronomoPropriedade.propriedade_id).where(
            VinculoAgronomoPropriedade.agronomo_id == usuario.id,
            VinculoAgronomoPropriedade.estado == EstadoVinculo.ACEITO,
        )
    )
    return list(resultado.scalars().all())
