"""Feature 012 — Cenários 1-3 de `specs/012-recomendacao-proximo-passo/quickstart.md`."""

import uuid
from datetime import UTC, date, datetime, timedelta
from unittest.mock import AsyncMock

import pytest
from geoalchemy2.functions import ST_SetSRID
from geoalchemy2.shape import from_shape
from httpx import ASGITransport, AsyncClient
from shapely.geometry import shape
from sqlalchemy import func
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.v1.endpoints import talhoes as talhoes_endpoint
from app.core.calculos.status_plantio import StatusPlantio
from app.core.security import UsuarioAutenticado, get_current_user, hash_senha
from app.db.models.balanco_hidrico_diario import BalancoHidricoDiario
from app.db.models.estacao_inmet import EstacaoInmet
from app.db.models.medicao_clima import FonteDados, MedicaoClima
from app.db.models.propriedade import Propriedade
from app.db.models.talhao import Talhao
from app.db.models.usuario import Papel, Usuario
from app.db.session import get_db
from app.main import app
from app.services.importacao_geo_service import normalizar_para_multipolygon
from tests.integration.conftest import limpar_tabelas

TALHAO_BELEM = {
    "type": "Polygon",
    "coordinates": [
        [[-48.50, -1.46], [-48.49, -1.46], [-48.49, -1.45], [-48.50, -1.45], [-48.50, -1.46]]
    ],
}
CAD_MM = 100.0


@pytest.fixture(autouse=True)
def sem_chamada_real_ao_soilgrids(monkeypatch):
    monkeypatch.setattr(talhoes_endpoint, "parametrizar_solo", AsyncMock(return_value=None))


@pytest.fixture
async def usuario_dono(pg_session: AsyncSession) -> Usuario:
    await limpar_tabelas(
        pg_session,
        "balanco_hidrico_diario",
        "talhoes",
        "propriedades",
        "medicoes_clima",
        "estacoes_inmet",
        "usuarios",
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
async def talhao(pg_session: AsyncSession, usuario_dono: Usuario) -> Talhao:
    propriedade = Propriedade(nome="Fazenda Teste", proprietario_id=usuario_dono.id)
    pg_session.add(propriedade)
    await pg_session.flush()

    talhao = Talhao(
        propriedade_id=propriedade.id,
        nome="Talhão Norte",
        geometria=from_shape(normalizar_para_multipolygon(shape(TALHAO_BELEM)), srid=4326),
        area_ha=1.0,
        capacidade_agua_disponivel_mm=CAD_MM,
    )
    pg_session.add(talhao)
    await pg_session.commit()
    await pg_session.refresh(talhao)
    return talhao


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


async def _inserir_balanco(
    pg_session: AsyncSession, talhao_id, data_alvo: date, armazenamento_mm: float, status: StatusPlantio
) -> None:
    pg_session.add(
        BalancoHidricoDiario(
            talhao_id=talhao_id,
            data=data_alvo,
            armazenamento_mm=armazenamento_mm,
            precipitacao_mm=0.0,
            evapotranspiracao_mm=1.0,
            status_plantio=status,
        )
    )
    await pg_session.commit()


@pytest.mark.asyncio
async def test_cenario_1_vermelho_e_sempre_alta(
    pg_session: AsyncSession, talhao: Talhao, client: AsyncClient
):
    await _inserir_balanco(pg_session, talhao.id, date.today(), 20.0, StatusPlantio.VERMELHO)

    resposta = await client.get(f"/api/v1/talhoes/{talhao.id}/recomendacao")

    assert resposta.status_code == 200
    dados = resposta.json()["dados"]
    assert dados["prioridade"] == "ALTA"
    assert "aviso" in dados and "não substitui avaliação agronômica" in dados["aviso"]


@pytest.mark.asyncio
async def test_cenario_2_bloqueio_de_pulverizacao_e_media(
    pg_session: AsyncSession, talhao: Talhao, client: AsyncClient
):
    await _inserir_balanco(pg_session, talhao.id, date.today(), 65.0, StatusPlantio.VERDE)

    posicao = ST_SetSRID(func.ST_MakePoint(-48.4950, -1.4550), 4326)
    pg_session.add(EstacaoInmet(codigo="A201", nome="Belém", estado="PA", posicao=posicao))
    pg_session.add(
        MedicaoClima(
            estacao_codigo="A201",
            data_hora_utc=datetime.now(UTC) - timedelta(minutes=5),
            precipitacao_mm=0.0,
            temperatura_c=28.0,
            umidade_pct=60.0,
            vento_velocidade_ms=4.0,  # 14.4 km/h > 10 -> vento forte
            vento_rajada_ms=3.0,
            fonte_dados=FonteDados.AO_VIVO,
        )
    )
    await pg_session.commit()

    resposta = await client.get(f"/api/v1/talhoes/{talhao.id}/recomendacao")

    assert resposta.status_code == 200
    dados = resposta.json()["dados"]
    assert dados["prioridade"] == "MEDIA"
    assert "vento forte" in dados["texto"]


@pytest.mark.asyncio
async def test_cenario_3_tendencia_subindo_no_texto(
    pg_session: AsyncSession, talhao: Talhao, client: AsyncClient
):
    hoje = date.today()
    await _inserir_balanco(pg_session, talhao.id, hoje - timedelta(days=3), 58.0, StatusPlantio.AMARELO)
    await _inserir_balanco(pg_session, talhao.id, hoje, 60.0, StatusPlantio.AMARELO)  # +2 p.p.

    resposta = await client.get(f"/api/v1/talhoes/{talhao.id}/recomendacao")

    assert resposta.status_code == 200
    dados = resposta.json()["dados"]
    assert dados["prioridade"] == "MEDIA"
    assert "melhorando" in dados["texto"]


@pytest.mark.asyncio
async def test_cenario_3_tendencia_caindo_no_texto(
    pg_session: AsyncSession, talhao: Talhao, client: AsyncClient
):
    hoje = date.today()
    await _inserir_balanco(pg_session, talhao.id, hoje - timedelta(days=3), 60.0, StatusPlantio.AMARELO)
    await _inserir_balanco(pg_session, talhao.id, hoje, 58.0, StatusPlantio.AMARELO)  # -2 p.p.

    resposta = await client.get(f"/api/v1/talhoes/{talhao.id}/recomendacao")

    assert "piorando" in resposta.json()["dados"]["texto"]


@pytest.mark.asyncio
async def test_talhao_sem_balanco_calculado_ainda(
    pg_session: AsyncSession, talhao: Talhao, client: AsyncClient
):
    resposta = await client.get(f"/api/v1/talhoes/{talhao.id}/recomendacao")

    assert resposta.status_code == 200
    dados = resposta.json()["dados"]
    assert dados["prioridade"] == "BAIXA"
    assert "ainda sem balanço hídrico" in dados["texto"].lower()
