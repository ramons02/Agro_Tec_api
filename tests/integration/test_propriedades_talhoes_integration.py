import json
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
from tests.integration.conftest import limpar_tabelas


@pytest.fixture(autouse=True)
def sem_chamada_real_ao_soilgrids(monkeypatch):
    """Estes testes cobrem CRUD/geometria (feature 005), não a parametrização de
    solo (feature 004) — evita depender de rede real/lenta a cada talhão criado.
    Simula "sem cobertura" (None), um cenário válido por si (FR-006)."""
    monkeypatch.setattr(talhoes_endpoint, "parametrizar_solo", AsyncMock(return_value=None))

# Fazenda Boa Esperança + "Talhão Norte", mesmo cenário de referência do protótipo.
TALHAO_NORTE = {
    "type": "Polygon",
    "coordinates": [[[-48.50, -1.46], [-48.49, -1.46], [-48.49, -1.45], [-48.50, -1.45], [-48.50, -1.46]]],
}
# Sobrepõe TALHAO_NORTE em boa parte da área.
TALHAO_SOBREPOSTO = {
    "type": "Polygon",
    "coordinates": [
        [[-48.495, -1.455], [-48.485, -1.455], [-48.485, -1.445], [-48.495, -1.445], [-48.495, -1.455]]
    ],
}
# Não toca TALHAO_NORTE.
TALHAO_SEM_SOBREPOSICAO = {
    "type": "Polygon",
    "coordinates": [[[-48.40, -1.40], [-48.39, -1.40], [-48.39, -1.39], [-48.40, -1.39], [-48.40, -1.40]]],
}
# Centroide fora da bounding box do Pará (região de São Paulo).
TALHAO_FORA_DO_PARA = {
    "type": "Polygon",
    "coordinates": [
        [[-46.64, -23.56], [-46.62, -23.56], [-46.62, -23.54], [-46.64, -23.54], [-46.64, -23.56]]
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
    assert resposta.status_code == 200
    return resposta.json()["dados"]["id"]


@pytest.mark.asyncio
async def test_cadastro_basico_propriedade_e_talhao(client: AsyncClient):
    propriedade_id = await _criar_propriedade(client)

    resposta = await client.post(
        "/api/v1/talhoes",
        json={"propriedade_id": propriedade_id, "nome": "Talhão Norte", "geometria": TALHAO_NORTE},
    )

    assert resposta.status_code == 200
    dados = resposta.json()["dados"]
    assert dados["nome"] == "Talhão Norte"
    assert dados["area_ha"] > 0


@pytest.mark.asyncio
async def test_sobreposicao_na_mesma_propriedade_e_bloqueada(client: AsyncClient):
    propriedade_id = await _criar_propriedade(client)
    await client.post(
        "/api/v1/talhoes",
        json={"propriedade_id": propriedade_id, "nome": "Talhão Norte", "geometria": TALHAO_NORTE},
    )

    resposta = await client.post(
        "/api/v1/talhoes",
        json={
            "propriedade_id": propriedade_id,
            "nome": "Talhão Sobreposto",
            "geometria": TALHAO_SOBREPOSTO,
        },
    )

    assert resposta.status_code == 409
    assert resposta.json()["detalhes"]["tipo"] == "SOBREPOSICAO"


@pytest.mark.asyncio
async def test_sem_sobreposicao_e_aceito(client: AsyncClient):
    propriedade_id = await _criar_propriedade(client)
    await client.post(
        "/api/v1/talhoes",
        json={"propriedade_id": propriedade_id, "nome": "Talhão Norte", "geometria": TALHAO_NORTE},
    )

    resposta = await client.post(
        "/api/v1/talhoes",
        json={
            "propriedade_id": propriedade_id,
            "nome": "Talhão Distante",
            "geometria": TALHAO_SEM_SOBREPOSICAO,
        },
    )

    assert resposta.status_code == 200


@pytest.mark.asyncio
async def test_fora_do_para_exige_confirmacao(client: AsyncClient):
    propriedade_id = await _criar_propriedade(client)

    sem_confirmacao = await client.post(
        "/api/v1/talhoes",
        json={
            "propriedade_id": propriedade_id,
            "nome": "Talhão Fronteira",
            "geometria": TALHAO_FORA_DO_PARA,
        },
    )
    assert sem_confirmacao.status_code == 422
    assert sem_confirmacao.json()["detalhes"]["requer_confirmacao"] is True

    com_confirmacao = await client.post(
        "/api/v1/talhoes",
        json={
            "propriedade_id": propriedade_id,
            "nome": "Talhão Fronteira",
            "geometria": TALHAO_FORA_DO_PARA,
            "confirmar_fora_do_para": True,
        },
    )
    assert com_confirmacao.status_code == 200


@pytest.mark.asyncio
async def test_sobreposicao_entre_propriedades_diferentes_e_permitida_com_aviso(
    client: AsyncClient,
):
    propriedade_a = await _criar_propriedade(client)
    await client.post(
        "/api/v1/talhoes",
        json={"propriedade_id": propriedade_a, "nome": "Talhão Norte", "geometria": TALHAO_NORTE},
    )

    resposta_propriedade_b = await client.post(
        "/api/v1/propriedades", json={"nome": "Fazenda Vizinha"}
    )
    propriedade_b = resposta_propriedade_b.json()["dados"]["id"]

    resposta = await client.post(
        "/api/v1/talhoes",
        json={
            "propriedade_id": propriedade_b,
            "nome": "Talhão Divisa",
            "geometria": TALHAO_SOBREPOSTO,
        },
    )

    assert resposta.status_code == 200
    assert "divisa em disputa" in resposta.json()["dados"]["aviso"]


@pytest.mark.asyncio
async def test_importar_geojson_cria_talhao(client: AsyncClient):
    propriedade_id = await _criar_propriedade(client)
    arquivo_geojson = json.dumps(TALHAO_NORTE).encode()

    resposta = await client.post(
        "/api/v1/talhoes/importar",
        data={"propriedade_id": propriedade_id, "nome": "Talhão Importado"},
        files={"arquivo": ("talhao.geojson", arquivo_geojson, "application/geo+json")},
    )

    assert resposta.status_code == 200
    dados = resposta.json()["dados"]
    assert dados["nome"] == "Talhão Importado"
    assert dados["area_ha"] > 0


@pytest.mark.asyncio
async def test_exclusao_de_propriedade_em_cascata(client: AsyncClient, pg_session: AsyncSession):
    propriedade_id = await _criar_propriedade(client)
    talhao_resposta = await client.post(
        "/api/v1/talhoes",
        json={"propriedade_id": propriedade_id, "nome": "Talhão Norte", "geometria": TALHAO_NORTE},
    )
    talhao_id = talhao_resposta.json()["dados"]["id"]

    exclusao = await client.delete(f"/api/v1/propriedades/{propriedade_id}")
    assert exclusao.status_code == 204

    talhao_depois = await client.get(f"/api/v1/talhoes/{talhao_id}")
    assert talhao_depois.status_code == 404
