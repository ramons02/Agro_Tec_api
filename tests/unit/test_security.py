import uuid
from datetime import UTC, datetime, timedelta

import pytest
from jose import jwt

from app.core.config import get_settings
from app.core.response import AppError
from app.core.security import (
    _decodificar_token,
    criar_token,
    hash_senha,
    verificar_senha,
)
from app.db.models.usuario import Papel


@pytest.mark.asyncio
async def test_hash_senha_gera_hash_diferente_da_senha_plana():
    senha_hash = await hash_senha("minha-senha-123")
    assert senha_hash != "minha-senha-123"


@pytest.mark.asyncio
async def test_verificar_senha_aceita_senha_correta():
    senha_hash = await hash_senha("minha-senha-123")
    assert await verificar_senha("minha-senha-123", senha_hash) is True


@pytest.mark.asyncio
async def test_verificar_senha_rejeita_senha_errada():
    senha_hash = await hash_senha("minha-senha-123")
    assert await verificar_senha("outra-senha", senha_hash) is False


def test_criar_token_expira_em_24_horas():
    usuario_id = uuid.uuid4()
    token, expira_em = criar_token(usuario_id, Papel.PRODUTOR_RURAL)

    settings = get_settings()
    payload = jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])

    assert payload["sub"] == str(usuario_id)
    assert payload["papel"] == "PRODUTOR_RURAL"
    delta = expira_em - datetime.now(UTC)
    assert timedelta(hours=23, minutes=59) < delta <= timedelta(hours=24)


def test_decodificar_token_valido():
    usuario_id = uuid.uuid4()
    token, _ = criar_token(usuario_id, Papel.GESTOR_TECNOLOGIA)

    usuario = _decodificar_token(token)

    assert usuario.id == usuario_id
    assert usuario.papel == Papel.GESTOR_TECNOLOGIA


def test_decodificar_token_invalido_levanta_401():
    with pytest.raises(AppError) as exc_info:
        _decodificar_token("token-completamente-invalido")

    assert exc_info.value.codigo == 401


def test_decodificar_token_expirado_levanta_401():
    settings = get_settings()
    claims = {
        "sub": str(uuid.uuid4()),
        "papel": Papel.PRODUTOR_RURAL.value,
        "exp": datetime.now(UTC) - timedelta(hours=1),
    }
    token_expirado = jwt.encode(claims, settings.jwt_secret, algorithm=settings.jwt_algorithm)

    with pytest.raises(AppError) as exc_info:
        _decodificar_token(token_expirado)

    assert exc_info.value.codigo == 401
