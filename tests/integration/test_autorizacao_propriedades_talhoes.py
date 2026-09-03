"""Feature 014 — matriz de autorização papel × ação × dono/vínculo (T011).

Cenários 1-4 de `specs/014-perfis-acesso-permissoes/quickstart.md`.
"""

import uuid
from contextlib import asynccontextmanager
from unittest.mock import AsyncMock

import pytest
from geoalchemy2.shape import from_shape
from httpx import ASGITransport, AsyncClient
from shapely.geometry import shape
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.v1.endpoints import talhoes as talhoes_endpoint
from app.core.security import UsuarioAutenticado, get_current_user, hash_senha
from app.db.models.propriedade import Propriedade
from app.db.models.talhao import Talhao
from app.db.models.usuario import Papel, Usuario
from app.db.models.vinculo_agronomo_propriedade import VinculoAgronomoPropriedade
from app.db.session import get_db
from app.main import app
from app.services.importacao_geo_service import normalizar_para_multipolygon
from tests.integration.conftest import limpar_tabelas

TALHAO_GENERICO = {
    "type": "Polygon",
    "coordinates": [
        [[-48.50, -1.46], [-48.49, -1.46], [-48.49, -1.45], [-48.50, -1.45], [-48.50, -1.46]]
    ],
}


@pytest.fixture(autouse=True)
def sem_chamada_real_ao_soilgrids(monkeypatch):
    monkeypatch.setattr(talhoes_endpoint, "parametrizar_solo", AsyncMock(return_value=None))


@asynccontextmanager
async def client_como(pg_session: AsyncSession, usuario: Usuario):
    async def _get_db_override():
        yield pg_session

    async def _get_current_user_override():
        return UsuarioAutenticado(id=usuario.id, papel=usuario.papel)

    app.dependency_overrides[get_db] = _get_db_override
    app.dependency_overrides[get_current_user] = _get_current_user_override
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()


async def _criar_usuario(pg_session: AsyncSession, papel: Papel) -> Usuario:
    usuario = Usuario(
        id=uuid.uuid4(),
        email=f"{papel.value.lower()}-{uuid.uuid4()}@exemplo.com",
        senha_hash=await hash_senha("senha-valida-123"),
        papel=papel,
    )
    pg_session.add(usuario)
    await pg_session.flush()
    return usuario


async def _criar_propriedade(pg_session: AsyncSession, dono: Usuario, nome: str = "Fazenda") -> Propriedade:
    propriedade = Propriedade(nome=nome, proprietario_id=dono.id)
    pg_session.add(propriedade)
    await pg_session.flush()
    return propriedade


async def _criar_talhao(pg_session: AsyncSession, propriedade: Propriedade) -> Talhao:
    talhao = Talhao(
        propriedade_id=propriedade.id,
        nome="Talhão Norte",
        geometria=from_shape(normalizar_para_multipolygon(shape(TALHAO_GENERICO)), srid=4326),
        area_ha=1.0,
    )
    pg_session.add(talhao)
    await pg_session.flush()
    return talhao


@pytest.fixture
async def cenario(pg_session: AsyncSession):
    await limpar_tabelas(
        pg_session, "vinculos_agronomo_propriedade", "talhoes", "propriedades", "usuarios"
    )
    produtor_a = await _criar_usuario(pg_session, Papel.PRODUTOR_RURAL)
    produtor_b = await _criar_usuario(pg_session, Papel.PRODUTOR_RURAL)
    agronomo = await _criar_usuario(pg_session, Papel.AGRONOMO)
    gestor = await _criar_usuario(pg_session, Papel.GESTOR_TECNOLOGIA)
    propriedade_a = await _criar_propriedade(pg_session, produtor_a, "Fazenda A")
    talhao_a = await _criar_talhao(pg_session, propriedade_a)
    await pg_session.commit()
    for obj in (produtor_a, produtor_b, agronomo, gestor, propriedade_a, talhao_a):
        await pg_session.refresh(obj)
    return {
        "produtor_a": produtor_a,
        "produtor_b": produtor_b,
        "agronomo": agronomo,
        "gestor": gestor,
        "propriedade_a": propriedade_a,
        "talhao_a": talhao_a,
    }


# --- Cenário 1: produtor não edita/exclui propriedade de outro ---------------


@pytest.mark.asyncio
async def test_produtor_edita_propria_propriedade(pg_session: AsyncSession, cenario):
    async with client_como(pg_session, cenario["produtor_a"]) as client:
        resposta = await client.put(
            f"/api/v1/propriedades/{cenario['propriedade_a'].id}", json={"nome": "Novo Nome"}
        )
    assert resposta.status_code == 200


@pytest.mark.asyncio
async def test_produtor_nao_edita_propriedade_de_outro(pg_session: AsyncSession, cenario):
    async with client_como(pg_session, cenario["produtor_b"]) as client:
        resposta = await client.put(
            f"/api/v1/propriedades/{cenario['propriedade_a'].id}", json={"nome": "Hackeado"}
        )
    assert resposta.status_code == 403
    assert resposta.json()["status"] == "erro"


@pytest.mark.asyncio
async def test_produtor_nao_exclui_talhao_de_outro(pg_session: AsyncSession, cenario):
    async with client_como(pg_session, cenario["produtor_b"]) as client:
        resposta = await client.delete(f"/api/v1/talhoes/{cenario['talhao_a'].id}")
    assert resposta.status_code == 403


# --- Cenário 2: agrônomo sem vínculo não vê a propriedade --------------------


@pytest.mark.asyncio
async def test_agronomo_sem_vinculo_lista_vazia(pg_session: AsyncSession, cenario):
    async with client_como(pg_session, cenario["agronomo"]) as client:
        resposta = await client.get("/api/v1/propriedades")
    assert resposta.status_code == 200
    assert resposta.json()["dados"]["itens"] == []


@pytest.mark.asyncio
async def test_agronomo_sem_vinculo_nao_acessa_propriedade_direto(pg_session: AsyncSession, cenario):
    async with client_como(pg_session, cenario["agronomo"]) as client:
        resposta = await client.get(f"/api/v1/propriedades/{cenario['propriedade_a'].id}")
    assert resposta.status_code == 404


@pytest.mark.asyncio
async def test_agronomo_nunca_escreve_mesmo_sem_vinculo(pg_session: AsyncSession, cenario):
    async with client_como(pg_session, cenario["agronomo"]) as client:
        resposta = await client.post("/api/v1/propriedades", json={"nome": "Fazenda do Agrônomo"})
    assert resposta.status_code == 403


# --- Cenário 3: convite + aceite ---------------------------------------------


@pytest.mark.asyncio
async def test_convite_sem_aceite_nao_da_acesso(pg_session: AsyncSession, cenario):
    vinculo = VinculoAgronomoPropriedade(
        agronomo_id=cenario["agronomo"].id, propriedade_id=cenario["propriedade_a"].id
    )
    pg_session.add(vinculo)
    await pg_session.commit()

    async with client_como(pg_session, cenario["agronomo"]) as client:
        resposta = await client.get("/api/v1/propriedades")
    assert resposta.json()["dados"]["itens"] == []


@pytest.mark.asyncio
async def test_convite_e_aceite_da_acesso_leitura(pg_session: AsyncSession, cenario):
    async with client_como(pg_session, cenario["produtor_a"]) as client:
        convite = await client.post(
            f"/api/v1/propriedades/{cenario['propriedade_a'].id}/vinculos",
            json={"agronomo_email": cenario["agronomo"].email},
        )
    assert convite.status_code == 201
    vinculo_id = convite.json()["dados"]["id"]

    async with client_como(pg_session, cenario["agronomo"]) as client:
        # antes do aceite, ainda sem acesso
        antes = await client.get("/api/v1/propriedades")
        assert antes.json()["dados"]["itens"] == []

        aceite = await client.post(f"/api/v1/vinculos/{vinculo_id}/aceitar")
        assert aceite.status_code == 200
        assert aceite.json()["dados"]["estado"] == "ACEITO"

        depois = await client.get("/api/v1/propriedades")
        assert len(depois.json()["dados"]["itens"]) == 1

        # leitura sim, escrita nunca (mesmo vinculado e aceito)
        escrita = await client.put(
            f"/api/v1/propriedades/{cenario['propriedade_a'].id}", json={"nome": "Tentativa"}
        )
        assert escrita.status_code == 403


@pytest.mark.asyncio
async def test_apenas_dono_ou_gestor_convida(pg_session: AsyncSession, cenario):
    async with client_como(pg_session, cenario["produtor_b"]) as client:
        resposta = await client.post(
            f"/api/v1/propriedades/{cenario['propriedade_a'].id}/vinculos",
            json={"agronomo_email": cenario["agronomo"].email},
        )
    assert resposta.status_code == 403


# --- Cenário 4: gestor de tecnologia tem acesso total ------------------------


@pytest.mark.asyncio
async def test_gestor_acessa_e_edita_qualquer_propriedade(pg_session: AsyncSession, cenario):
    async with client_como(pg_session, cenario["gestor"]) as client:
        leitura = await client.get(f"/api/v1/propriedades/{cenario['propriedade_a'].id}")
        assert leitura.status_code == 200

        escrita = await client.put(
            f"/api/v1/propriedades/{cenario['propriedade_a'].id}", json={"nome": "Editado pelo gestor"}
        )
        assert escrita.status_code == 200


@pytest.mark.asyncio
async def test_gestor_ve_todas_as_propriedades_na_listagem(pg_session: AsyncSession, cenario):
    await _criar_propriedade(pg_session, cenario["produtor_b"], "Fazenda B")
    await pg_session.commit()

    async with client_como(pg_session, cenario["gestor"]) as client:
        resposta = await client.get("/api/v1/propriedades")
    assert resposta.json()["dados"]["total"] == 2
