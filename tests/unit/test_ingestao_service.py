from datetime import UTC, datetime
from unittest.mock import AsyncMock

import pytest

from app.db.models.estacao_inmet import EstacaoInmet
from app.services import ingestao_service, inmet_service, openmeteo_service
from app.services.inmet_service import FonteIndisponivelError, MedicaoInmetDTO
from app.services.openmeteo_service import FontePrevisaoIndisponivelError, PrevisaoClimatica


@pytest.fixture
def estacao() -> EstacaoInmet:
    return EstacaoInmet(codigo="A201", nome="Belém", estado="PA", posicao="POINT(-48.43 -1.41)")


@pytest.mark.asyncio
async def test_ingerir_estacao_sucesso_direto_do_inmet(monkeypatch, estacao):
    medicao = MedicaoInmetDTO(
        estacao_codigo="A201",
        data_hora_utc=datetime.now(UTC),
        precipitacao_mm=1.0,
        temperatura_c=28.0,
        umidade_pct=60.0,
        vento_velocidade_ms=2.0,
        vento_rajada_ms=4.0,
    )
    monkeypatch.setattr(inmet_service, "buscar_medicao_recente", AsyncMock(return_value=medicao))
    db = AsyncMock()

    resultado = await ingestao_service.ingerir_estacao(db, estacao)

    assert resultado == "sucesso"
    db.execute.assert_awaited_once()


@pytest.mark.asyncio
async def test_ingerir_estacao_aciona_fallback_quando_inmet_falha(monkeypatch, estacao):
    monkeypatch.setattr(
        inmet_service,
        "buscar_medicao_recente",
        AsyncMock(side_effect=FonteIndisponivelError("timeout simulado")),
    )
    monkeypatch.setattr(
        ingestao_service, "_coordenadas_estacao", AsyncMock(return_value=(-1.41, -48.43))
    )
    previsao = PrevisaoClimatica(
        latitude=-1.41,
        longitude=-48.43,
        vento_10m_kmh=10.8,
        vento_100m_kmh=15.0,
        evapotranspiracao_mm=4.0,
        umidade_solo_0_7cm=0.3,
        umidade_solo_outras_camadas={},
        precipitacao_prevista_mm=0.0,
        obtido_em_utc=datetime.now(UTC),
    )
    monkeypatch.setattr(
        openmeteo_service, "obter_previsao", AsyncMock(return_value=previsao)
    )
    db = AsyncMock()

    resultado = await ingestao_service.ingerir_estacao(db, estacao)

    assert resultado == "fallback"
    db.execute.assert_awaited_once()


@pytest.mark.asyncio
async def test_ingerir_estacao_falha_total_quando_ambas_fontes_falham(monkeypatch, estacao):
    monkeypatch.setattr(
        inmet_service,
        "buscar_medicao_recente",
        AsyncMock(side_effect=FonteIndisponivelError("timeout simulado")),
    )
    monkeypatch.setattr(
        ingestao_service, "_coordenadas_estacao", AsyncMock(return_value=(-1.41, -48.43))
    )
    monkeypatch.setattr(
        openmeteo_service,
        "obter_previsao",
        AsyncMock(side_effect=FontePrevisaoIndisponivelError("também indisponível")),
    )
    db = AsyncMock()

    resultado = await ingestao_service.ingerir_estacao(db, estacao)

    assert resultado == "falha_total"
    db.execute.assert_not_awaited()
