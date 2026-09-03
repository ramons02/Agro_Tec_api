import pytest
from httpx import AsyncClient

from app.db.models.usuario import Usuario


@pytest.mark.asyncio
async def test_login_com_credenciais_validas_retorna_token(
    client: AsyncClient, usuario_teste: Usuario
):
    resposta = await client.post(
        "/api/v1/auth/login",
        json={"email": "produtor@exemplo.com", "senha": "senha-valida-123"},
    )

    assert resposta.status_code == 200
    corpo = resposta.json()
    assert corpo["status"] == "sucesso"
    assert corpo["dados"]["token"]
    assert corpo["dados"]["papel"] == "PRODUTOR_RURAL"


@pytest.mark.asyncio
async def test_login_com_senha_errada_retorna_401_generico(
    client: AsyncClient, usuario_teste: Usuario
):
    resposta = await client.post(
        "/api/v1/auth/login",
        json={"email": "produtor@exemplo.com", "senha": "senha-errada"},
    )

    assert resposta.status_code == 401
    corpo = resposta.json()
    assert corpo["status"] == "erro"
    assert corpo["mensagem"] == "Usuário ou senha inválidos."


@pytest.mark.asyncio
async def test_login_com_email_inexistente_retorna_mesma_mensagem_generica(client: AsyncClient):
    resposta = await client.post(
        "/api/v1/auth/login",
        json={"email": "ninguem@exemplo.com", "senha": "qualquer-coisa"},
    )

    assert resposta.status_code == 401
    assert resposta.json()["mensagem"] == "Usuário ou senha inválidos."


@pytest.mark.asyncio
async def test_rota_protegida_sem_token_retorna_401(client: AsyncClient):
    resposta = await client.get("/api/v1/auth/me")

    assert resposta.status_code == 401
    assert resposta.json()["mensagem"] == "Token de autorização ausente ou expirado."


@pytest.mark.asyncio
async def test_rota_protegida_com_token_valido_retorna_200(
    client: AsyncClient, usuario_teste: Usuario
):
    login = await client.post(
        "/api/v1/auth/login",
        json={"email": "produtor@exemplo.com", "senha": "senha-valida-123"},
    )
    token = login.json()["dados"]["token"]

    resposta = await client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})

    assert resposta.status_code == 200
    assert resposta.json()["dados"]["papel"] == "PRODUTOR_RURAL"


@pytest.mark.asyncio
async def test_rota_protegida_com_token_malformado_retorna_401(client: AsyncClient):
    resposta = await client.get(
        "/api/v1/auth/me", headers={"Authorization": "Bearer token-invalido"}
    )

    assert resposta.status_code == 401
