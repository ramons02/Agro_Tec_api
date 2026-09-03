import uuid
from unittest.mock import AsyncMock

import pytest
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.v1.endpoints import talhoes as talhoes_endpoint
from app.core.security import UsuarioAutenticado, get_current_user, hash_senha
from app.db.models.usuario import Papel, Usuario
from app.db.session import get_db
from app.main import app
from app.services.soilgrids_service import FonteSoloIndisponivelError, PerfilSoloDTO
from tests.integration.conftest import limpar_tabelas

TALHAO_NORTE = {
    "type": "Polygon",
    "coordinates": [
        [[-48.50, -1.46], [-48.49, -1.46], [-48.49, -1.45], [-48.50, -1.45], [-48.50, -1.46]]
    ],
}


@pytest.fixture
async def usuario_dono(pg_session: AsyncSession) -> Usuario:
    await limpar_tabelas(pg_session, "talhoes", "propriedades", "usuarios")
    usuario = Usuario(
        id=uuid.uuid4(),
        email=f"produtor-{uuid.uuid4()}@exemplo.com",
        senha_hash=await hash_senha("senha-valida-123"),
        papel=Papel.PRODUTOR_RURAL,
    )
    pg_session.add(usuario)
    await pg_session.commit()
    await pg_session.refresh(usuario)
    return usuario


@pytest.fixture
async def client(pg_session: AsyncSession, usuario_dono: Usuario):
    async def _get_db_override():
        yield pg_session

    async def _get_current_user_override():
        return UsuarioAutenticado(id=usuario_dono.id, papel=usuario_dono.papel)

    app.dependency_overrides[get_db] = _get_db_override
    app.dependency_overrides[get_current_user] = _get_current_user_override
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()


async def _criar_propriedade(client: AsyncClient) -> str:
    resposta = await client.post("/api/v1/propriedades", json={"nome": "Fazenda Boa Esperança"})
    return resposta.json()["dados"]["id"]


@pytest.mark.asyncio
async def test_talhao_recebe_tipo_solo_e_cad_quando_soilgrids_responde(
    client: AsyncClient, monkeypatch
):
    perfil = PerfilSoloDTO(
        fracao_argila_pct=45.0,
        fracao_areia_pct=20.0,
        fracao_silte_pct=35.0,
        materia_organica_pct=2.5,
        densidade_solo_g_cm3=1.3,
    )
    monkeypatch.setattr(
        talhoes_endpoint, "parametrizar_solo", AsyncMock(return_value=perfil)
    )
    propriedade_id = await _criar_propriedade(client)

    resposta = await client.post(
        "/api/v1/talhoes",
        json={"propriedade_id": propriedade_id, "nome": "Talhão Norte", "geometria": TALHAO_NORTE},
    )

    assert resposta.status_code == 200
    dados = resposta.json()["dados"]
    assert dados["tipo_solo"] == "ARGILOSO"  # argila 45% >= 35% (limiar)
    assert dados["capacidade_agua_disponivel_mm"] is not None
    assert dados["capacidade_agua_disponivel_mm"] > 0


@pytest.mark.asyncio
async def test_talhao_sem_cobertura_soilgrids_salva_com_solo_nulo(
    client: AsyncClient, monkeypatch
):
    monkeypatch.setattr(
        talhoes_endpoint, "parametrizar_solo", AsyncMock(return_value=None)
    )
    propriedade_id = await _criar_propriedade(client)

    resposta = await client.post(
        "/api/v1/talhoes",
        json={"propriedade_id": propriedade_id, "nome": "Talhão Norte", "geometria": TALHAO_NORTE},
    )

    assert resposta.status_code == 200
    dados = resposta.json()["dados"]
    assert dados["tipo_solo"] is None
    assert dados["capacidade_agua_disponivel_mm"] is None


@pytest.mark.asyncio
async def test_talhao_com_soilgrids_indisponivel_nao_bloqueia_cadastro(
    client: AsyncClient, monkeypatch
):
    monkeypatch.setattr(
        talhoes_endpoint,
        "parametrizar_solo",
        AsyncMock(side_effect=FonteSoloIndisponivelError("timeout simulado")),
    )
    propriedade_id = await _criar_propriedade(client)

    resposta = await client.post(
        "/api/v1/talhoes",
        json={"propriedade_id": propriedade_id, "nome": "Talhão Norte", "geometria": TALHAO_NORTE},
    )

    assert resposta.status_code == 200
    assert resposta.json()["dados"]["tipo_solo"] is None
