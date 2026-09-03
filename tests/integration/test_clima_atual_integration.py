import asyncio
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
from app.services import clima_tempo_real_service
from app.services.inmet_service import FonteIndisponivelError, MedicaoInmetDTO
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


@pytest.fixture(autouse=True)
def limpar_locks_entre_testes():
    """Locks são globais no módulo (por estação) — evita vazamento entre testes."""
    clima_tempo_real_service._locks_por_estacao.clear()
    yield
    clima_tempo_real_service._locks_por_estacao.clear()


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
    estacao = EstacaoInmet(codigo="A201", nome="Belém", estado="PA", posicao=posicao)
    pg_session.add(estacao)

    propriedade = Propriedade(nome="Fazenda Teste", proprietario_id=usuario_dono.id)
    pg_session.add(propriedade)
    await pg_session.flush()

    talhao = Talhao(
        propriedade_id=propriedade.id,
        nome="Talhão Norte",
        geometria=from_shape(shape(TALHAO_BELEM), srid=4326),
        area_ha=1.0,
    )
    pg_session.add(talhao)
    await pg_session.commit()
    await pg_session.refresh(talhao)
    return talhao


async def _inserir_medicao(
    pg_session: AsyncSession,
    idade: timedelta,
    fonte_dados: FonteDados = FonteDados.AO_VIVO,
    vento_ms: float = 2.0,
) -> None:
    pg_session.add(
        MedicaoClima(
            estacao_codigo="A201",
            data_hora_utc=datetime.now(UTC) - idade,
            precipitacao_mm=5.0,
            temperatura_c=28.0,
            umidade_pct=60.0,
            vento_velocidade_ms=vento_ms,
            vento_rajada_ms=vento_ms + 1,
            fonte_dados=fonte_dados,
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
async def test_medicao_fresca_retorna_direto_sem_buscar(
    client: AsyncClient, talhao_com_estacao: Talhao, pg_session: AsyncSession, monkeypatch
):
    await _inserir_medicao(pg_session, idade=timedelta(minutes=5))
    busca_mock = AsyncMock()
    monkeypatch.setattr("app.services.ingestao_service.inmet_service.buscar_medicao_recente", busca_mock)

    resposta = await client.get(f"/api/v1/clima/atual?talhao_id={talhao_com_estacao.id}")

    assert resposta.status_code == 200
    dados = resposta.json()["dados"]
    assert dados["fonte_dados"] == "AO_VIVO"
    assert dados["vento_kmh"] == pytest.approx(7.2)  # 2.0 m/s * 3.6
    busca_mock.assert_not_awaited()


@pytest.mark.asyncio
async def test_headers_no_cache_sempre_presentes(
    client: AsyncClient, talhao_com_estacao: Talhao, pg_session: AsyncSession
):
    await _inserir_medicao(pg_session, idade=timedelta(minutes=5))

    resposta = await client.get(f"/api/v1/clima/atual?talhao_id={talhao_com_estacao.id}&_t=123")

    assert resposta.headers["Cache-Control"] == "no-cache, no-store, must-revalidate"
    assert resposta.headers["Pragma"] == "no-cache"


@pytest.mark.asyncio
async def test_medicao_expirada_dispara_busca_e_atualiza(
    client: AsyncClient, talhao_com_estacao: Talhao, pg_session: AsyncSession, monkeypatch
):
    await _inserir_medicao(pg_session, idade=timedelta(minutes=45))
    nova_medicao = MedicaoInmetDTO(
        estacao_codigo="A201",
        data_hora_utc=datetime.now(UTC),
        precipitacao_mm=0.0,
        temperatura_c=30.0,
        umidade_pct=55.0,
        vento_velocidade_ms=3.0,
        vento_rajada_ms=5.0,
    )
    monkeypatch.setattr(
        "app.services.ingestao_service.inmet_service.buscar_medicao_recente",
        AsyncMock(return_value=nova_medicao),
    )

    resposta = await client.get(f"/api/v1/clima/atual?talhao_id={talhao_com_estacao.id}")

    assert resposta.status_code == 200
    dados = resposta.json()["dados"]
    assert dados["fonte_dados"] == "AO_VIVO"
    assert dados["vento_kmh"] == pytest.approx(10.8)  # 3.0 m/s * 3.6


@pytest.mark.asyncio
async def test_todas_as_fontes_falham_retorna_cache_expirado_sem_erro(
    client: AsyncClient, talhao_com_estacao: Talhao, pg_session: AsyncSession, monkeypatch
):
    await _inserir_medicao(pg_session, idade=timedelta(minutes=45), vento_ms=2.5)
    monkeypatch.setattr(
        "app.services.ingestao_service.inmet_service.buscar_medicao_recente",
        AsyncMock(side_effect=FonteIndisponivelError("timeout")),
    )
    monkeypatch.setattr(
        "app.services.ingestao_service.openmeteo_service.obter_previsao",
        AsyncMock(side_effect=FontePrevisaoIndisponivelError("timeout")),
    )

    resposta = await client.get(f"/api/v1/clima/atual?talhao_id={talhao_com_estacao.id}")

    assert resposta.status_code == 200
    dados = resposta.json()["dados"]
    assert dados["fonte_dados"] == "CACHE_EXPIRADO"
    assert dados["vento_kmh"] == pytest.approx(9.0)  # 2.5 * 3.6 — a medicao antiga, nao um erro


@pytest.mark.asyncio
async def test_duas_requisicoes_simultaneas_disparam_uma_unica_busca(
    talhao_com_estacao: Talhao, pg_session: AsyncSession, monkeypatch
):
    await _inserir_medicao(pg_session, idade=timedelta(minutes=45))
    contador = {"chamadas": 0}

    async def busca_lenta(codigo):
        contador["chamadas"] += 1
        await asyncio.sleep(0.05)
        return MedicaoInmetDTO(
            estacao_codigo=codigo,
            data_hora_utc=datetime.now(UTC),
            precipitacao_mm=1.0,
            temperatura_c=29.0,
            umidade_pct=58.0,
            vento_velocidade_ms=4.0,
            vento_rajada_ms=6.0,
        )

    monkeypatch.setattr(
        "app.services.ingestao_service.inmet_service.buscar_medicao_recente", busca_lenta
    )

    talhao_recarregado = await pg_session.get(Talhao, talhao_com_estacao.id)
    resultados = await asyncio.gather(
        clima_tempo_real_service.obter_clima_atual(pg_session, talhao_recarregado),
        clima_tempo_real_service.obter_clima_atual(pg_session, talhao_recarregado),
    )

    assert contador["chamadas"] == 1
    assert all(r is not None and r.fonte_dados == FonteDados.AO_VIVO for r in resultados)


@pytest.mark.asyncio
async def test_talhao_sem_nenhuma_medicao_e_falha_total_retorna_404(
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

    resposta = await client.get(f"/api/v1/clima/atual?talhao_id={talhao_com_estacao.id}")

    assert resposta.status_code == 404


@pytest.mark.asyncio
async def test_talhao_inexistente_retorna_404(client: AsyncClient):
    resposta = await client.get(f"/api/v1/clima/atual?talhao_id={uuid.uuid4()}")
    assert resposta.status_code == 404
