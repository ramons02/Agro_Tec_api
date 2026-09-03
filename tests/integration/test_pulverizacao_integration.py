import uuid
from datetime import UTC, datetime, timedelta
from unittest.mock import AsyncMock

import pytest
from geoalchemy2.functions import ST_SetSRID
from geoalchemy2.shape import from_shape
from httpx import ASGITransport, AsyncClient
from shapely.geometry import shape
from sqlalchemy import func
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.v1.endpoints import talhoes as talhoes_endpoint
from app.core.security import UsuarioAutenticado, get_current_user, hash_senha
from app.db.models.estacao_inmet import EstacaoInmet
from app.db.models.medicao_clima import FonteDados, MedicaoClima
from app.db.models.propriedade import Propriedade
from app.db.models.talhao import Talhao
from app.db.models.usuario import Papel, Usuario
from app.db.session import get_db
from app.main import app
from app.services.importacao_geo_service import normalizar_para_multipolygon
from app.services.inmet_service import FonteIndisponivelError
from app.services.openmeteo_service import FontePrevisaoIndisponivelError
from tests.integration.conftest import limpar_tabelas

TALHAO_BELEM = {
    "type": "Polygon",
    "coordinates": [
        [[-48.50, -1.46], [-48.49, -1.46], [-48.49, -1.45], [-48.50, -1.45], [-48.50, -1.46]]
    ],
}


@pytest.fixture(autouse=True)
def sem_chamada_real_ao_soilgrids(monkeypatch):
    monkeypatch.setattr(talhoes_endpoint, "parametrizar_solo", AsyncMock(return_value=None))


@pytest.fixture
async def usuario_dono(pg_session: AsyncSession) -> Usuario:
    await limpar_tabelas(
        pg_session, "talhoes", "propriedades", "medicoes_clima", "estacoes_inmet", "usuarios"
    )
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
async def talhao_com_estacao(pg_session: AsyncSession, usuario_dono: Usuario) -> Talhao:
    posicao = ST_SetSRID(func.ST_MakePoint(-48.4950, -1.4550), 4326)
    pg_session.add(EstacaoInmet(codigo="A201", nome="Belém", estado="PA", posicao=posicao))

    propriedade = Propriedade(nome="Fazenda Teste", proprietario_id=usuario_dono.id)
    pg_session.add(propriedade)
    await pg_session.flush()

    talhao = Talhao(
        propriedade_id=propriedade.id,
        nome="Talhão Norte",
        geometria=from_shape(normalizar_para_multipolygon(shape(TALHAO_BELEM)), srid=4326),
        area_ha=1.0,
    )
    pg_session.add(talhao)
    await pg_session.commit()
    await pg_session.refresh(talhao)
    return talhao


async def _inserir_medicao(pg_session: AsyncSession, vento_ms: float, rajada_ms: float) -> None:
    pg_session.add(
        MedicaoClima(
            estacao_codigo="A201",
            data_hora_utc=datetime.now(UTC) - timedelta(minutes=5),
            precipitacao_mm=0.0,
            temperatura_c=28.0,
            umidade_pct=60.0,
            vento_velocidade_ms=vento_ms,
            vento_rajada_ms=rajada_ms,
            fonte_dados=FonteDados.AO_VIVO,
        )
    )
    await pg_session.commit()


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


@pytest.mark.asyncio
async def test_pulverizacao_favoravel(
    client: AsyncClient, talhao_com_estacao: Talhao, pg_session: AsyncSession
):
    await _inserir_medicao(pg_session, vento_ms=2.0, rajada_ms=3.0)  # 7.2 km/h, 10.8 km/h

    resposta = await client.get(f"/api/v1/talhoes/{talhao_com_estacao.id}/pulverizacao")

    assert resposta.status_code == 200
    dados = resposta.json()["dados"]
    assert dados["classificacao"] == "FAVORAVEL"


@pytest.mark.asyncio
async def test_pulverizacao_bloqueio_vento_forte(
    client: AsyncClient, talhao_com_estacao: Talhao, pg_session: AsyncSession
):
    await _inserir_medicao(pg_session, vento_ms=4.0, rajada_ms=3.0)  # 14.4 km/h > 10

    resposta = await client.get(f"/api/v1/talhoes/{talhao_com_estacao.id}/pulverizacao")

    assert resposta.json()["dados"]["classificacao"] == "BLOQUEIO_VENTO_FORTE"


@pytest.mark.asyncio
async def test_pulverizacao_bloqueio_inversao_termica(
    client: AsyncClient, talhao_com_estacao: Talhao, pg_session: AsyncSession
):
    await _inserir_medicao(pg_session, vento_ms=0.5, rajada_ms=0.8)  # 1.8 km/h < 3

    resposta = await client.get(f"/api/v1/talhoes/{talhao_com_estacao.id}/pulverizacao")

    assert resposta.json()["dados"]["classificacao"] == "BLOQUEIO_INVERSAO_TERMICA"


@pytest.mark.asyncio
async def test_sem_leitura_de_vento_nao_apresenta_classificacao_falsa(
    client: AsyncClient, talhao_com_estacao: Talhao, monkeypatch
):
    monkeypatch.setattr(
        "app.services.ingestao_service.inmet_service.buscar_medicao_recente",
        AsyncMock(side_effect=FonteIndisponivelError("timeout")),
    )
    monkeypatch.setattr(
        "app.services.ingestao_service.openmeteo_service.obter_previsao",
        AsyncMock(side_effect=FontePrevisaoIndisponivelError("timeout")),
    )

    resposta = await client.get(f"/api/v1/talhoes/{talhao_com_estacao.id}/pulverizacao")

    assert resposta.status_code == 404
