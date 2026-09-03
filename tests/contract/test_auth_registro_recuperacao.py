from datetime import UTC, datetime, timedelta

import pytest
from httpx import AsyncClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.token_recuperacao_senha import TokenRecuperacaoSenha
from app.db.models.usuario import Usuario

# --- User Story 1: cadastro de conta -----------------------------------------


@pytest.mark.asyncio
async def test_registro_cria_conta_e_permite_login(client: AsyncClient):
    resposta = await client.post(
        "/api/v1/auth/registro",
        json={
            "nome": "Ana Ferreira",
            "email": "ana@exemplo.com",
            "senha": "senha-valida-123",
            "papel": "PRODUTOR_RURAL",
        },
    )

    assert resposta.status_code == 201
    dados = resposta.json()["dados"]
    assert dados["nome"] == "Ana Ferreira"
    assert "senha_hash" not in dados
    assert "senha" not in dados

    login = await client.post(
        "/api/v1/auth/login", json={"email": "ana@exemplo.com", "senha": "senha-valida-123"}
    )
    assert login.status_code == 200


@pytest.mark.asyncio
async def test_registro_com_email_duplicado_e_recusado(client: AsyncClient, usuario_teste: Usuario):
    resposta = await client.post(
        "/api/v1/auth/registro",
        json={
            "nome": "Outro Nome",
            "email": usuario_teste.email,
            "senha": "outra-senha-123",
            "papel": "PRODUTOR_RURAL",
        },
    )

    assert resposta.status_code == 409
    assert resposta.json()["mensagem"] == "Email já cadastrado."


@pytest.mark.asyncio
async def test_registro_com_senha_curta_e_recusado(client: AsyncClient):
    resposta = await client.post(
        "/api/v1/auth/registro",
        json={"nome": "Ana", "email": "ana2@exemplo.com", "senha": "curta", "papel": "AGRONOMO"},
    )

    assert resposta.status_code == 422


# --- User Story 2: recuperação de senha --------------------------------------


@pytest.mark.asyncio
async def test_recuperar_senha_gera_token_e_permite_redefinir(
    client: AsyncClient, usuario_teste: Usuario, db_session: AsyncSession
):
    resposta = await client.post(
        "/api/v1/auth/recuperar-senha", json={"email": usuario_teste.email}
    )
    assert resposta.status_code == 200
    assert resposta.json()["dados"]["mensagem"] == "Se o email existir, um link de redefinição foi enviado."

    token = (
        await db_session.execute(
            select(TokenRecuperacaoSenha).where(TokenRecuperacaoSenha.usuario_id == usuario_teste.id)
        )
    ).scalar_one()

    redefinir = await client.post(
        "/api/v1/auth/redefinir-senha",
        json={"token": token.token, "nova_senha": "nova-senha-456"},
    )
    assert redefinir.status_code == 200

    login = await client.post(
        "/api/v1/auth/login", json={"email": usuario_teste.email, "senha": "nova-senha-456"}
    )
    assert login.status_code == 200


@pytest.mark.asyncio
async def test_token_ja_usado_e_rejeitado(
    client: AsyncClient, usuario_teste: Usuario, db_session: AsyncSession
):
    await client.post("/api/v1/auth/recuperar-senha", json={"email": usuario_teste.email})
    token = (
        await db_session.execute(
            select(TokenRecuperacaoSenha).where(TokenRecuperacaoSenha.usuario_id == usuario_teste.id)
        )
    ).scalar_one()

    primeira = await client.post(
        "/api/v1/auth/redefinir-senha", json={"token": token.token, "nova_senha": "senha-nova-1"}
    )
    assert primeira.status_code == 200

    segunda = await client.post(
        "/api/v1/auth/redefinir-senha", json={"token": token.token, "nova_senha": "senha-nova-2"}
    )
    assert segunda.status_code == 400
    assert "inválido ou expirado" in segunda.json()["mensagem"]


@pytest.mark.asyncio
async def test_token_expirado_e_rejeitado(
    client: AsyncClient, usuario_teste: Usuario, db_session: AsyncSession
):
    token_expirado = TokenRecuperacaoSenha(
        usuario_id=usuario_teste.id,
        token="token-de-teste-expirado",
        criado_em=datetime.now(UTC) - timedelta(hours=2),
        expira_em=datetime.now(UTC) - timedelta(hours=1),
    )
    db_session.add(token_expirado)
    await db_session.commit()

    resposta = await client.post(
        "/api/v1/auth/redefinir-senha",
        json={"token": "token-de-teste-expirado", "nova_senha": "senha-nova-123"},
    )
    assert resposta.status_code == 400


@pytest.mark.asyncio
async def test_token_inexistente_e_rejeitado(client: AsyncClient):
    resposta = await client.post(
        "/api/v1/auth/redefinir-senha",
        json={"token": "token-que-nao-existe", "nova_senha": "senha-nova-123"},
    )
    assert resposta.status_code == 400


@pytest.mark.asyncio
async def test_recuperacao_para_email_inexistente_tem_resposta_identica(client: AsyncClient):
    """FR-006 — nunca revela se o email existe."""
    existente = await client.post(
        "/api/v1/auth/recuperar-senha", json={"email": "existe@exemplo.com"}
    )
    inexistente = await client.post(
        "/api/v1/auth/recuperar-senha", json={"email": "nao-existe@exemplo.com"}
    )

    assert existente.status_code == inexistente.status_code == 200
    assert existente.json()["dados"] == inexistente.json()["dados"]
