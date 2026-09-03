"""Feature 007 — Cenários 1-2 de `specs/007-mapa-interativo-talhoes/quickstart.md`."""

import uuid
from datetime import UTC, datetime, timedelta

import pytest
from geoalchemy2.functions import ST_SetSRID
from geoalchemy2.shape import from_shape
from httpx import ASGITransport, AsyncClient
from shapely.geometry import shape
from sqlalchemy import func
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import UsuarioAutenticado, get_current_user, hash_senha
from app.db.models.estacao_inmet import EstacaoInmet
from app.db.models.medicao_clima import FonteDados, MedicaoClima
from app.db.models.propriedade import Propriedade
from app.db.models.talhao import Talhao
from app.db.models.usuario import Papel, Usuario
from app.db.models.vinculo_agronomo_propriedade import EstadoVinculo, VinculoAgronomoPropriedade
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


async def _client_como(pg_session: AsyncSession, usuario: Usuario) -> AsyncClient:
    async def _get_db_override():
        yield pg_session

    async def _get_current_user_override():
        return UsuarioAutenticado(id=usuario.id, papel=usuario.papel)

    app.dependency_overrides[get_db] = _get_db_override
    app.dependency_overrides[get_current_user] = _get_current_user_override
    transport = ASGITransport(app=app)
    return AsyncClient(transport=transport, base_url="http://test")


@pytest.fixture
async def cenario(pg_session: AsyncSession):
    await limpar_tabelas(
        pg_session,
        "vinculos_agronomo_propriedade",
        "medicoes_clima",
        "estacoes_inmet",
        "talhoes",
        "propriedades",
        "usuarios",
    )
    produtor = Usuario(
        id=uuid.uuid4(),
        email=f"produtor-{uuid.uuid4()}@exemplo.com",
        senha_hash=await hash_senha("senha-valida-123"),
        papel=Papel.PRODUTOR_RURAL,
    )
    agronomo = Usuario(
        id=uuid.uuid4(),
        email=f"agronomo-{uuid.uuid4()}@exemplo.com",
        senha_hash=await hash_senha("senha-valida-123"),
        papel=Papel.AGRONOMO,
    )
    pg_session.add_all([produtor, agronomo])
    await pg_session.flush()

    propriedade = Propriedade(nome="Fazenda Teste", proprietario_id=produtor.id)
    pg_session.add(propriedade)
    await pg_session.flush()

    talhao = Talhao(
        propriedade_id=propriedade.id,
        nome="Talhão Norte",
        geometria=from_shape(normalizar_para_multipolygon(shape(TALHAO_GENERICO)), srid=4326),
        area_ha=1.0,
    )
    pg_session.add(talhao)

    posicao = ST_SetSRID(func.ST_MakePoint(-48.4950, -1.4550), 4326)
    pg_session.add(EstacaoInmet(codigo="A201", nome="Belém", estado="PA", posicao=posicao))
    await pg_session.flush()

    pg_session.add(
        MedicaoClima(
            estacao_codigo="A201",
            data_hora_utc=datetime.now(UTC) - timedelta(minutes=5),
            precipitacao_mm=4.2,
            temperatura_c=28.0,
            umidade_pct=70.0,
            vento_velocidade_ms=2.0,
            vento_rajada_ms=3.0,
            fonte_dados=FonteDados.AO_VIVO,
        )
    )
    await pg_session.commit()

    return {"produtor": produtor, "agronomo": agronomo, "propriedade": propriedade, "talhao": talhao}


@pytest.mark.asyncio
async def test_payload_traz_geometrias_geojson_e_status_plantio(pg_session: AsyncSession, cenario):
    async with await _client_como(pg_session, cenario["produtor"]) as client:
        resposta = await client.get("/api/v1/mapa/dados")

    assert resposta.status_code == 200
    dados = resposta.json()["dados"]
    assert len(dados["propriedades"]) == 1
    talhao = dados["propriedades"][0]["talhoes"][0]
    assert talhao["geometria_geojson"]["type"] == "MultiPolygon"
    assert talhao["status_plantio"] is None  # sem balanço hídrico calculado ainda

    estacao = next(e for e in dados["estacoes"] if e["codigo"] == "A201")
    assert estacao["posicao_geojson"]["type"] == "Point"
    assert estacao["ultima_medicao"]["chuva_mm"] == 4.2
    assert estacao["ultima_medicao"]["vento_kmh"] == pytest.approx(7.2, abs=0.1)


@pytest.mark.asyncio
async def test_agronomo_sem_vinculo_nao_ve_propriedade(pg_session: AsyncSession, cenario):
    async with await _client_como(pg_session, cenario["agronomo"]) as client:
        resposta = await client.get("/api/v1/mapa/dados")

    assert resposta.json()["dados"]["propriedades"] == []


@pytest.mark.asyncio
async def test_agronomo_com_vinculo_aceito_ve_so_essa_propriedade(pg_session: AsyncSession, cenario):
    pg_session.add(
        VinculoAgronomoPropriedade(
            agronomo_id=cenario["agronomo"].id,
            propriedade_id=cenario["propriedade"].id,
            estado=EstadoVinculo.ACEITO,
        )
    )
    await pg_session.commit()

    async with await _client_como(pg_session, cenario["agronomo"]) as client:
        resposta = await client.get("/api/v1/mapa/dados")

    dados = resposta.json()["dados"]
    assert len(dados["propriedades"]) == 1
    assert dados["propriedades"][0]["id"] == str(cenario["propriedade"].id)
    # Estações são infraestrutura pública, sem RBAC — visíveis mesmo sem vínculo.
    assert len(dados["estacoes"]) == 1
