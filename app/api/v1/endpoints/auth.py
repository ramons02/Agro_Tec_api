import secrets
from datetime import UTC, datetime
from typing import Annotated

from fastapi import APIRouter, Depends
from pydantic import BaseModel, EmailStr, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings, get_settings
from app.core.response import AppError, envelope_sucesso
from app.core.security import (
    UsuarioAutenticado,
    criar_token,
    get_current_user,
    hash_senha,
    verificar_senha,
)
from app.db.models.token_recuperacao_senha import VALIDADE_TOKEN_RECUPERACAO, TokenRecuperacaoSenha
from app.db.models.usuario import Papel, Usuario
from app.db.session import get_db
from app.services.email_service import obter_email_service

router = APIRouter(prefix="/auth", tags=["auth"])

MENSAGEM_CREDENCIAIS_INVALIDAS = "Usuário ou senha inválidos."
MENSAGEM_TOKEN_INVALIDO = "Link de redefinição inválido ou expirado. Solicite um novo."
MENSAGEM_RECUPERACAO_ENVIADA = "Se o email existir, um link de redefinição foi enviado."


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


class RegistroRequest(BaseModel):
    nome: str
    email: EmailStr
    senha: str = Field(min_length=8)
    papel: Papel


@router.post("/registro", status_code=201)
async def registro(
    payload: RegistroRequest,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    """Feature 013/FR-001-FR-003 — cadastro de conta; email único (409), senha
    com hash bcrypt (nunca em texto puro), mínimo 8 caracteres (422 via Pydantic)."""
    existente = (
        await db.execute(select(Usuario).where(Usuario.email == payload.email))
    ).scalar_one_or_none()
    if existente is not None:
        raise AppError(409, "Email já cadastrado.")

    usuario = Usuario(
        nome=payload.nome,
        email=payload.email,
        senha_hash=await hash_senha(payload.senha),
        papel=payload.papel,
    )
    db.add(usuario)
    await db.commit()
    await db.refresh(usuario)
    return envelope_sucesso(
        {"id": str(usuario.id), "nome": usuario.nome, "email": usuario.email, "papel": usuario.papel.value}
    )


class RecuperarSenhaRequest(BaseModel):
    email: EmailStr


@router.post("/recuperar-senha")
async def recuperar_senha(
    payload: RecuperarSenhaRequest,
    db: Annotated[AsyncSession, Depends(get_db)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> dict:
    """Feature 013/FR-004/FR-006 — resposta idêntica exista ou não o email
    (nunca revela quais emails têm conta); token opaco de uso único, 1h de
    validade, nunca o JWT de sessão (research.md)."""
    usuario = (
        await db.execute(select(Usuario).where(Usuario.email == payload.email))
    ).scalar_one_or_none()

    if usuario is not None:
        agora = datetime.now(UTC)
        token_recuperacao = TokenRecuperacaoSenha(
            usuario_id=usuario.id,
            token=secrets.token_urlsafe(32),
            criado_em=agora,
            expira_em=agora + VALIDADE_TOKEN_RECUPERACAO,
        )
        db.add(token_recuperacao)
        await db.commit()

        link = f"{settings.frontend_base_url}/#/redefinir-senha?token={token_recuperacao.token}"
        email_service = obter_email_service(settings)
        await email_service.enviar_recuperacao_senha(usuario.email, link)

    return envelope_sucesso({"mensagem": MENSAGEM_RECUPERACAO_ENVIADA})


class RedefinirSenhaRequest(BaseModel):
    token: str
    nova_senha: str = Field(min_length=8)


@router.post("/redefinir-senha")
async def redefinir_senha(
    payload: RedefinirSenhaRequest,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    """Feature 013/FR-005 — token expirado ou já usado é sempre 400 com
    mensagem clara, nunca um erro genérico."""
    token_recuperacao = (
        await db.execute(
            select(TokenRecuperacaoSenha).where(TokenRecuperacaoSenha.token == payload.token)
        )
    ).scalar_one_or_none()

    agora = datetime.now(UTC)
    expira_em = token_recuperacao.expira_em if token_recuperacao is not None else None
    if expira_em is not None and expira_em.tzinfo is None:
        # SQLite (usado nos testes de contrato) não preserva timezone em
        # DateTime(timezone=True) — Postgres (produção) sempre devolve aware.
        expira_em = expira_em.replace(tzinfo=UTC)

    if (
        token_recuperacao is None
        or token_recuperacao.usado_em is not None
        or expira_em < agora
    ):
        raise AppError(400, MENSAGEM_TOKEN_INVALIDO)

    usuario = await db.get(Usuario, token_recuperacao.usuario_id)
    usuario.senha_hash = await hash_senha(payload.nova_senha)
    token_recuperacao.usado_em = agora
    await db.commit()

    return envelope_sucesso({"mensagem": "Senha redefinida com sucesso."})
