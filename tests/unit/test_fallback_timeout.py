from datetime import UTC, datetime

import httpx
import pytest

from app.services import inmet_service
from app.services.inmet_service import (
    FonteIndisponivelError,
    _parsear_medicao_mais_recente,
    buscar_medicao_recente,
)

REGISTROS_EXEMPLO = [
    {
        "CD_ESTACAO": "A201",
        "DT_MEDICAO": "2026-09-03",
        "HR_MEDICAO": "1100",
        "TEM_INS": "27.8",
        "UMD_INS": "68",
        "VEN_VEL": "2.5",
        "VEN_RAJ": "4.0",
        "CHUVA": "0.0",
    },
    {
        "CD_ESTACAO": "A201",
        "DT_MEDICAO": "2026-09-03",
        "HR_MEDICAO": "1200",
        "TEM_INS": "28.4",
        "UMD_INS": "65",
        "VEN_VEL": "3.2",
        "VEN_RAJ": "6.1",
        "CHUVA": "0.2",
    },
    {
        # hora ainda não publicada pelo INMET — deve ser ignorada
        "CD_ESTACAO": "A201",
        "DT_MEDICAO": "2026-09-03",
        "HR_MEDICAO": "1300",
        "TEM_INS": None,
        "UMD_INS": None,
        "VEN_VEL": None,
        "VEN_RAJ": None,
        "CHUVA": None,
    },
]

_AsyncClientOriginal = httpx.AsyncClient


def _client_mockado(handler):
    def _client_factory(*args, **kwargs):
        kwargs["transport"] = httpx.MockTransport(handler)
        return _AsyncClientOriginal(*args, **kwargs)

    return _client_factory


def test_parsear_medicao_mais_recente_ignora_hora_sem_leitura():
    medicao = _parsear_medicao_mais_recente("A201", REGISTROS_EXEMPLO)

    assert medicao is not None
    assert medicao.data_hora_utc == datetime(2026, 9, 3, 12, 0, tzinfo=UTC)
    assert medicao.temperatura_c == 28.4
    assert medicao.vento_velocidade_ms == 3.2
    assert medicao.vento_rajada_ms == 6.1
    assert medicao.precipitacao_mm == 0.2


def test_parsear_medicao_retorna_none_sem_nenhuma_leitura_valida():
    registros_todos_vazios = [r | {"TEM_INS": None, "VEN_VEL": None} for r in REGISTROS_EXEMPLO]

    assert _parsear_medicao_mais_recente("A201", registros_todos_vazios) is None


@pytest.mark.asyncio
async def test_buscar_medicao_recente_timeout_levanta_fonte_indisponivel(monkeypatch):
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.TimeoutException("timeout simulado", request=request)

    monkeypatch.setattr(httpx, "AsyncClient", _client_mockado(handler))

    with pytest.raises(FonteIndisponivelError):
        await buscar_medicao_recente("A201")


@pytest.mark.asyncio
async def test_buscar_medicao_recente_erro_http_levanta_fonte_indisponivel(monkeypatch):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(503)

    monkeypatch.setattr(httpx, "AsyncClient", _client_mockado(handler))

    with pytest.raises(FonteIndisponivelError):
        await buscar_medicao_recente("A201")


@pytest.mark.asyncio
async def test_buscar_medicao_recente_sucesso(monkeypatch):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=REGISTROS_EXEMPLO)

    monkeypatch.setattr(httpx, "AsyncClient", _client_mockado(handler))

    medicao = await buscar_medicao_recente("A201")

    assert medicao is not None
    assert medicao.estacao_codigo == "A201"
    assert medicao.temperatura_c == 28.4


@pytest.mark.asyncio
async def test_buscar_estacoes_pa_filtra_por_estado(monkeypatch):
    catalogo = [
        {
            "CD_ESTACAO": "A201",
            "DC_NOME": "BELEM",
            "SG_ESTADO": "PA",
            "VL_LATITUDE": "-1.41",
            "VL_LONGITUDE": "-48.43",
        },
        {
            "CD_ESTACAO": "A999",
            "DC_NOME": "OUTRO ESTADO",
            "SG_ESTADO": "SP",
            "VL_LATITUDE": "-23.5",
            "VL_LONGITUDE": "-46.6",
        },
    ]

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=catalogo)

    monkeypatch.setattr(httpx, "AsyncClient", _client_mockado(handler))

    estacoes = await inmet_service.buscar_estacoes_pa()

    assert len(estacoes) == 1
    assert estacoes[0].codigo == "A201"
