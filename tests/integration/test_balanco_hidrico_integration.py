import uuid
from datetime import UTC, date, datetime, timedelta
from unittest.mock import AsyncMock

import pytest
from geoalchemy2.functions import ST_SetSRID
from geoalchemy2.shape import from_shape
from shapely.geometry import shape
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.calculos.status_plantio import StatusPlantio
from app.db.models.balanco_hidrico_diario import BalancoHidricoDiario
from app.db.models.estacao_inmet import EstacaoInmet
from app.db.models.medicao_clima import FonteDados, MedicaoClima
from app.db.models.propriedade import Propriedade
from app.db.models.talhao import Talhao, TipoSolo
from app.db.models.usuario import Papel, Usuario
from app.services import balanco_hidrico_service
from app.services.openmeteo_service import PrevisaoClimatica
from tests.integration.conftest import limpar_tabelas

TALHAO_BELEM = {
    "type": "Polygon",
    "coordinates": [
        [[-48.50, -1.46], [-48.49, -1.46], [-48.49, -1.45], [-48.50, -1.45], [-48.50, -1.46]]
    ],
}


@pytest.fixture
async def talhao_com_cad(pg_session: AsyncSession) -> Talhao:
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
        senha_hash="hash-fake",
        papel=Papel.PRODUTOR_RURAL,
    )
    pg_session.add(usuario)

    posicao = ST_SetSRID(func.ST_MakePoint(-48.4950, -1.4550), 4326)
    pg_session.add(EstacaoInmet(codigo="A201", nome="Belém", estado="PA", posicao=posicao))

    await pg_session.flush()
    propriedade = Propriedade(nome="Fazenda Teste", proprietario_id=usuario.id)
    pg_session.add(propriedade)
    await pg_session.flush()

    talhao = Talhao(
        propriedade_id=propriedade.id,
        nome="Talhão Norte",
        geometria=from_shape(shape(TALHAO_BELEM), srid=4326),
        area_ha=1.0,
        tipo_solo=TipoSolo.MISTO,
        capacidade_agua_disponivel_mm=60.0,
    )
    pg_session.add(talhao)
    await pg_session.commit()
    await pg_session.refresh(talhao)
    return talhao


def _mock_previsao(et0_mm: float, precipitacao_prevista_mm: float = 0.0) -> PrevisaoClimatica:
    return PrevisaoClimatica(
        latitude=-1.455,
        longitude=-48.495,
        vento_10m_kmh=5.0,
        vento_100m_kmh=8.0,
        evapotranspiracao_mm=et0_mm,
        umidade_solo_0_7cm=0.3,
        umidade_solo_outras_camadas={},
        precipitacao_prevista_mm=precipitacao_prevista_mm,
        obtido_em_utc=datetime.now(UTC),
    )


async def _adicionar_chuva(pg_session: AsyncSession, mm: float) -> None:
    pg_session.add(
        MedicaoClima(
            estacao_codigo="A201",
            data_hora_utc=datetime.now(UTC),
            precipitacao_mm=mm,
            temperatura_c=28.0,
            umidade_pct=60.0,
            vento_velocidade_ms=2.0,
            vento_rajada_ms=3.0,
            fonte_dados=FonteDados.AO_VIVO,
        )
    )
    await pg_session.commit()


@pytest.mark.asyncio
async def test_primeiro_calculo_usa_armazenamento_inicial_e_persiste(
    pg_session: AsyncSession, talhao_com_cad: Talhao, monkeypatch
):
    await _adicionar_chuva(pg_session, mm=5.0)
    monkeypatch.setattr(
        balanco_hidrico_service, "obter_previsao", AsyncMock(return_value=_mock_previsao(et0_mm=3.0))
    )

    resultado = await balanco_hidrico_service.calcular_balanco_hidrico_do_talhao(
        pg_session, talhao_com_cad
    )
    await pg_session.commit()

    # ARM_0 = 0.70*60 = 42; Kc=0.4 -> ET_real=1.2; 42+5-1.2 = 45.8
    assert resultado is not None
    assert resultado.armazenamento_mm == pytest.approx(45.8)

    persistido = (
        await pg_session.execute(
            select(BalancoHidricoDiario).where(BalancoHidricoDiario.talhao_id == talhao_com_cad.id)
        )
    ).scalar_one()
    assert float(persistido.armazenamento_mm) == pytest.approx(45.8)


@pytest.mark.asyncio
async def test_segundo_dia_usa_armazenamento_do_dia_anterior(
    pg_session: AsyncSession, talhao_com_cad: Talhao, monkeypatch
):
    ontem = date.today() - timedelta(days=1)
    pg_session.add(
        BalancoHidricoDiario(
            talhao_id=talhao_com_cad.id,
            data=ontem,
            armazenamento_mm=50.0,
            precipitacao_mm=0.0,
            evapotranspiracao_mm=2.0,
            status_plantio=StatusPlantio.AMARELO,
        )
    )
    await pg_session.commit()
    await _adicionar_chuva(pg_session, mm=0.0)
    monkeypatch.setattr(
        balanco_hidrico_service, "obter_previsao", AsyncMock(return_value=_mock_previsao(et0_mm=5.0))
    )

    resultado = await balanco_hidrico_service.calcular_balanco_hidrico_do_talhao(
        pg_session, talhao_com_cad
    )

    # ARM_anterior=50, P=0, ET_real=5*0.4=2 -> 48
    assert resultado.armazenamento_mm == pytest.approx(48.0)


@pytest.mark.asyncio
async def test_armazenamento_nunca_ultrapassa_a_cad(
    pg_session: AsyncSession, talhao_com_cad: Talhao, monkeypatch
):
    await _adicionar_chuva(pg_session, mm=100.0)  # chuva muito forte
    monkeypatch.setattr(
        balanco_hidrico_service, "obter_previsao", AsyncMock(return_value=_mock_previsao(et0_mm=1.0))
    )

    resultado = await balanco_hidrico_service.calcular_balanco_hidrico_do_talhao(
        pg_session, talhao_com_cad
    )

    assert resultado.armazenamento_mm == 60.0  # teto = CAD


@pytest.mark.asyncio
async def test_armazenamento_nunca_fica_negativo(
    pg_session: AsyncSession, talhao_com_cad: Talhao, monkeypatch
):
    ontem = date.today() - timedelta(days=1)
    pg_session.add(
        BalancoHidricoDiario(
            talhao_id=talhao_com_cad.id,
            data=ontem,
            armazenamento_mm=2.0,
            precipitacao_mm=0.0,
            evapotranspiracao_mm=1.0,
            status_plantio=StatusPlantio.VERMELHO,
        )
    )
    await pg_session.commit()
    monkeypatch.setattr(
        balanco_hidrico_service,
        "obter_previsao",
        AsyncMock(return_value=_mock_previsao(et0_mm=50.0)),  # ET altissima, sem chuva
    )

    resultado = await balanco_hidrico_service.calcular_balanco_hidrico_do_talhao(
        pg_session, talhao_com_cad
    )

    assert resultado.armazenamento_mm == 0.0


@pytest.mark.asyncio
async def test_talhao_sem_cad_nao_calcula(pg_session: AsyncSession, talhao_com_cad: Talhao):
    talhao_com_cad.capacidade_agua_disponivel_mm = None
    await pg_session.commit()

    resultado = await balanco_hidrico_service.calcular_balanco_hidrico_do_talhao(
        pg_session, talhao_com_cad
    )

    assert resultado is None


@pytest.mark.asyncio
async def test_recalculo_no_mesmo_dia_e_idempotente_via_upsert(
    pg_session: AsyncSession, talhao_com_cad: Talhao, monkeypatch
):
    monkeypatch.setattr(
        balanco_hidrico_service, "obter_previsao", AsyncMock(return_value=_mock_previsao(et0_mm=3.0))
    )

    await balanco_hidrico_service.calcular_balanco_hidrico_do_talhao(pg_session, talhao_com_cad)
    await pg_session.commit()
    await balanco_hidrico_service.calcular_balanco_hidrico_do_talhao(pg_session, talhao_com_cad)
    await pg_session.commit()

    linhas = (
        await pg_session.execute(
            select(BalancoHidricoDiario).where(BalancoHidricoDiario.talhao_id == talhao_com_cad.id)
        )
    ).scalars().all()
    assert len(linhas) == 1
