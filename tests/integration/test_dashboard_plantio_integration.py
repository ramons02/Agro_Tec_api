import uuid
from datetime import date
from unittest.mock import AsyncMock

import pytest
from geoalchemy2.shape import from_shape
from httpx import ASGITransport, AsyncClient
from shapely.geometry import shape
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.v1.endpoints import talhoes as talhoes_endpoint
from app.core.calculos.status_plantio import StatusPlantio
from app.core.security import UsuarioAutenticado, get_current_user, hash_senha
from app.db.models.balanco_hidrico_diario import BalancoHidricoDiario
from app.db.models.propriedade import Propriedade
from app.db.models.talhao import Talhao, TipoSolo
from app.db.models.usuario import Papel, Usuario
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


@pytest.fixture
async def usuario_dono(pg_session: AsyncSession) -> Usuario:
    await limpar_tabelas(
        pg_session, "balanco_hidrico_diario", "talhoes", "propriedades", "usuarios"
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


async def _criar_talhao_com_status(
    pg_session: AsyncSession, propriedade_id, nome: str, status: StatusPlantio
) -> Talhao:
    talhao = Talhao(
        propriedade_id=propriedade_id,
        nome=nome,
        geometria=from_shape(normalizar_para_multipolygon(shape(TALHAO_GENERICO)), srid=4326),
        area_ha=5.0,
        tipo_solo=TipoSolo.MISTO,
        capacidade_agua_disponivel_mm=60.0,
    )
    pg_session.add(talhao)
    await pg_session.flush()
    pg_session.add(
        BalancoHidricoDiario(
            talhao_id=talhao.id,
            data=date.today(),
            armazenamento_mm=42.0,
            precipitacao_mm=5.0,
            evapotranspiracao_mm=2.0,
            status_plantio=status,
        )
    )
    await pg_session.commit()
    await pg_session.refresh(talhao)
    return talhao


@pytest.fixture
async def tres_talhoes_status_distintos(pg_session: AsyncSession, usuario_dono: Usuario):
    propriedade = Propriedade(nome="Fazenda Boa Esperança", proprietario_id=usuario_dono.id)
    pg_session.add(propriedade)
    await pg_session.flush()
    await pg_session.commit()

    verde = await _criar_talhao_com_status(pg_session, propriedade.id, "Verde", StatusPlantio.VERDE)
    amarelo = await _criar_talhao_com_status(
        pg_session, propriedade.id, "Amarelo", StatusPlantio.AMARELO
    )
    vermelho = await _criar_talhao_com_status(
        pg_session, propriedade.id, "Vermelho", StatusPlantio.VERMELHO
    )
    return propriedade, verde, amarelo, vermelho


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
async def test_lista_todos_os_talhoes_com_status(
    client: AsyncClient, tres_talhoes_status_distintos
):
    resposta = await client.get("/api/v1/dashboard/plantio")

    assert resposta.status_code == 200
    dados = resposta.json()["dados"]
    assert dados["total"] == 3
    status_encontrados = {item["status_plantio"] for item in dados["itens"]}
    assert status_encontrados == {"VERDE", "AMARELO", "VERMELHO"}


@pytest.mark.asyncio
async def test_filtro_por_status(client: AsyncClient, tres_talhoes_status_distintos):
    resposta = await client.get("/api/v1/dashboard/plantio?status=VERMELHO")

    dados = resposta.json()["dados"]
    assert dados["total"] == 1
    assert dados["itens"][0]["nome"] == "Vermelho"


@pytest.mark.asyncio
async def test_filtro_por_propriedade(client: AsyncClient, tres_talhoes_status_distintos):
    propriedade, *_ = tres_talhoes_status_distintos

    resposta = await client.get(f"/api/v1/dashboard/plantio?propriedade_id={propriedade.id}")

    assert resposta.json()["dados"]["total"] == 3


@pytest.mark.asyncio
async def test_paginacao(client: AsyncClient, tres_talhoes_status_distintos):
    resposta = await client.get("/api/v1/dashboard/plantio?page=1&page_size=2")

    dados = resposta.json()["dados"]
    assert dados["total"] == 3
    assert len(dados["itens"]) == 2
    assert dados["page"] == 1
    assert dados["page_size"] == 2


@pytest.mark.asyncio
async def test_talhao_sem_balanco_calculado_aparece_com_status_nulo(
    client: AsyncClient, pg_session: AsyncSession, usuario_dono: Usuario
):
    propriedade = Propriedade(nome="Fazenda Nova", proprietario_id=usuario_dono.id)
    pg_session.add(propriedade)
    await pg_session.flush()
    talhao = Talhao(
        propriedade_id=propriedade.id,
        nome="Talhão Recém-Cadastrado",
        geometria=from_shape(normalizar_para_multipolygon(shape(TALHAO_GENERICO)), srid=4326),
        area_ha=3.0,
    )
    pg_session.add(talhao)
    await pg_session.commit()

    resposta = await client.get("/api/v1/dashboard/plantio")

    dados = resposta.json()["dados"]
    item = next(i for i in dados["itens"] if i["nome"] == "Talhão Recém-Cadastrado")
    assert item["status_plantio"] is None


# --- Feature 015: exportação CSV ---------------------------------------------


@pytest.mark.asyncio
async def test_exportacao_csv_tem_bom_utf8_e_content_disposition(
    client: AsyncClient, tres_talhoes_status_distintos
):
    resposta = await client.get("/api/v1/dashboard/plantio/exportar.csv")

    assert resposta.status_code == 200
    assert resposta.headers["content-type"].startswith("text/csv")
    assert "attachment" in resposta.headers["content-disposition"]
    assert resposta.content.startswith(b"\xef\xbb\xbf")  # BOM UTF-8 (FR-003)


@pytest.mark.asyncio
async def test_exportacao_csv_contem_todos_os_talhoes_filtrados(
    client: AsyncClient, tres_talhoes_status_distintos
):
    resposta = await client.get("/api/v1/dashboard/plantio/exportar.csv")

    texto = resposta.content.decode("utf-8-sig")
    linhas = texto.strip().splitlines()
    assert len(linhas) == 4  # cabeçalho + 3 talhões
    assert "Propriedade;Talhao;Area (ha);Solo;Status;Armazenamento (mm);% da CAD" == linhas[0]
    nomes_exportados = {linha.split(";")[1] for linha in linhas[1:]}
    assert nomes_exportados == {"Verde", "Amarelo", "Vermelho"}


@pytest.mark.asyncio
async def test_exportacao_csv_respeita_filtro_de_status(
    client: AsyncClient, tres_talhoes_status_distintos
):
    resposta = await client.get("/api/v1/dashboard/plantio/exportar.csv?status=VERMELHO")

    texto = resposta.content.decode("utf-8-sig")
    linhas = [linha for linha in texto.strip().splitlines() if linha]
    assert len(linhas) == 2  # cabeçalho + 1 talhão
    assert "Vermelho" in linhas[1]


@pytest.mark.asyncio
async def test_exportacao_csv_sem_talhoes_retorna_so_cabecalho(client: AsyncClient):
    resposta = await client.get("/api/v1/dashboard/plantio/exportar.csv")

    texto = resposta.content.decode("utf-8-sig")
    linhas = [linha for linha in texto.strip().splitlines() if linha]
    assert len(linhas) == 1
