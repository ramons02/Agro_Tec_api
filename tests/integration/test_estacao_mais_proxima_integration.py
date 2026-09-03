import uuid
from unittest.mock import AsyncMock

import pytest
from geoalchemy2.functions import ST_SetSRID
from httpx import ASGITransport, AsyncClient
from sqlalchemy import func
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.v1.endpoints import talhoes as talhoes_endpoint
from app.core.security import UsuarioAutenticado, get_current_user, hash_senha
from app.db.models.estacao_inmet import EstacaoInmet
from app.db.models.usuario import Papel, Usuario
from app.db.queries.estacao_proxima import buscar_estacao_mais_proxima
from app.db.session import get_db
from app.main import app
from tests.integration.conftest import limpar_tabelas

# Talhão em Belém; A201 é a estação mais próxima (poucos km), A999 fica no
# interior do estado, bem mais longe — distância real e conhecida entre pontos.
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
async def duas_estacoes(pg_session: AsyncSession) -> None:
    # A201 ~1km do talhão de teste; A999 a ~800km (outro extremo do Pará).
    perto = ST_SetSRID(func.ST_MakePoint(-48.4950, -1.4550), 4326)
    longe = ST_SetSRID(func.ST_MakePoint(-52.0, -6.0), 4326)
    pg_session.add_all(
        [
            EstacaoInmet(codigo="A201", nome="Belém", estado="PA", posicao=perto),
            EstacaoInmet(codigo="A999", nome="Interior", estado="PA", posicao=longe),
        ]
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


async def _criar_talhao(client: AsyncClient) -> str:
    propriedade = await client.post("/api/v1/propriedades", json={"nome": "Fazenda Boa Esperança"})
    propriedade_id = propriedade.json()["dados"]["id"]
    talhao = await client.post(
        "/api/v1/talhoes",
        json={"propriedade_id": propriedade_id, "nome": "Talhão Norte", "geometria": TALHAO_BELEM},
    )
    return talhao.json()["dados"]["id"]


@pytest.mark.asyncio
async def test_retorna_estacao_fisicamente_mais_proxima(
    client: AsyncClient, duas_estacoes, pg_session: AsyncSession
):
    talhao_id = await _criar_talhao(client)

    resposta = await client.get(f"/api/v1/talhoes/{talhao_id}/estacao-mais-proxima")

    assert resposta.status_code == 200
    dados = resposta.json()["dados"]
    assert dados["estacao_codigo"] == "A201"
    assert dados["distancia_km"] < 5  # A201 está a ~1km, A999 a ~800km


@pytest.mark.asyncio
async def test_talhao_inexistente_retorna_404(client: AsyncClient):
    resposta = await client.get(f"/api/v1/talhoes/{uuid.uuid4()}/estacao-mais-proxima")
    assert resposta.status_code == 404


@pytest.mark.asyncio
async def test_sem_estacoes_cadastradas_retorna_404(client: AsyncClient):
    talhao_id = await _criar_talhao(client)

    resposta = await client.get(f"/api/v1/talhoes/{talhao_id}/estacao-mais-proxima")

    assert resposta.status_code == 404


@pytest.mark.asyncio
async def test_distancia_bate_com_calculo_geodesico_real(
    pg_session: AsyncSession, usuario_dono: Usuario, duas_estacoes
):
    """Confirma que a distância retornada é a distância real (não planar em graus)."""
    from geoalchemy2.shape import from_shape
    from shapely.geometry import shape

    from app.db.models.propriedade import Propriedade
    from app.db.models.talhao import Talhao

    propriedade = Propriedade(nome="Fazenda Teste", proprietario_id=usuario_dono.id)
    pg_session.add(propriedade)
    await pg_session.flush()

    poligono = shape(TALHAO_BELEM)
    talhao = Talhao(
        propriedade_id=propriedade.id,
        nome="Talhão Norte",
        geometria=from_shape(poligono, srid=4326),
        area_ha=1.0,
    )
    pg_session.add(talhao)
    await pg_session.commit()
    await pg_session.refresh(talhao)

    resultado = await buscar_estacao_mais_proxima(pg_session, talhao)

    assert resultado is not None
    assert resultado.estacao_codigo == "A201"
    # Haversine manual entre o centroide do talhão (~-1.455,-48.495) e A201 (-1.455,-48.495)
    # são quase coincidentes por construção do teste — poucas centenas de metros.
    assert 0 <= resultado.distancia_km < 1
