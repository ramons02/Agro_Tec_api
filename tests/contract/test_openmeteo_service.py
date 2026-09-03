import httpx
import pytest

from app.services import openmeteo_service
from app.services.openmeteo_service import (
    FontePrevisaoIndisponivelError,
    obter_previsao,
)

PAYLOAD_EXEMPLO = {
    # Formato real da Open-Meteo: todas as variáveis horárias num único objeto
    # "hourly" (não há "hourly_soil" separado — ver bug corrigido em
    # openmeteo_service._parsear_resposta).
    "hourly": {
        "time": ["2026-09-03T00:00", "2026-09-03T01:00"],
        "wind_speed_10m": [7.2, 8.1],
        "wind_speed_100m": [12.4, 13.0],
        "soil_moisture_0_to_7cm": [0.34, 0.33],
        "soil_moisture_7_to_28cm": [0.29, 0.28],
    },
    "daily": {
        "et0_fao_evapotranspiration": [4.2],
        "precipitation_sum": [8.5],
    },
}


@pytest.fixture(autouse=True)
def limpar_cache_e_contador():
    openmeteo_service._cache.clear()
    openmeteo_service._contador_chamadas_reais = 0
    yield
    openmeteo_service._cache.clear()
    openmeteo_service._contador_chamadas_reais = 0


_AsyncClientOriginal = httpx.AsyncClient


def _client_mockado(handler):
    def _client_factory(*args, **kwargs):
        kwargs["transport"] = httpx.MockTransport(handler)
        return _AsyncClientOriginal(*args, **kwargs)

    return _client_factory


@pytest.mark.asyncio
async def test_obter_previsao_estrutura_dados_corretamente(monkeypatch):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=PAYLOAD_EXEMPLO)

    monkeypatch.setattr(httpx, "AsyncClient", _client_mockado(handler))

    previsao = await obter_previsao(latitude=-1.4558, longitude=-48.4902)

    assert previsao.vento_10m_kmh == 7.2
    assert previsao.vento_100m_kmh == 12.4
    assert previsao.evapotranspiracao_mm == 4.2
    assert previsao.precipitacao_prevista_mm == 8.5
    assert previsao.umidade_solo_0_7cm == 0.34
    assert "soil_moisture_7_to_28cm" in previsao.umidade_solo_outras_camadas
    assert "time" not in previsao.umidade_solo_outras_camadas
    assert "wind_speed_10m" not in previsao.umidade_solo_outras_camadas


@pytest.mark.asyncio
async def test_obter_previsao_timeout_levanta_erro_especifico(monkeypatch):
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.TimeoutException("timeout simulado", request=request)

    monkeypatch.setattr(httpx, "AsyncClient", _client_mockado(handler))

    with pytest.raises(FontePrevisaoIndisponivelError):
        await obter_previsao(latitude=-1.4558, longitude=-48.4902)


@pytest.mark.asyncio
async def test_obter_previsao_reaproveita_cache_para_coordenadas_proximas(monkeypatch):
    chamadas = {"total": 0}

    def handler(request: httpx.Request) -> httpx.Response:
        chamadas["total"] += 1
        return httpx.Response(200, json=PAYLOAD_EXEMPLO)

    monkeypatch.setattr(httpx, "AsyncClient", _client_mockado(handler))

    await obter_previsao(latitude=-1.4558, longitude=-48.4902)
    await obter_previsao(latitude=-1.4559, longitude=-48.4903)  # mesma coord arredondada
    await obter_previsao(latitude=-1.4558, longitude=-48.4902)

    assert chamadas["total"] == 1
    assert openmeteo_service.contador_chamadas_reais() == 1
