import logging
from datetime import UTC, datetime

import httpx
from cachetools import TTLCache
from pydantic import BaseModel

logger = logging.getLogger(__name__)

OPENMETEO_URL = "https://api.open-meteo.com/v1/forecast"
TIMEOUT_SEGUNDOS = 3.0
CACHE_TTL_SEGUNDOS = 30 * 60  # 30min — mesmo limiar de staleness da feature 008

# Chave de cache por coordenada arredondada + hora (research.md): talhões próximos
# compartilham essencialmente a mesma previsão, reduzindo o volume de chamadas
# reais para respeitar o limite gratuito de 10.000/dia (RNF012).
_cache: TTLCache[tuple[float, float, str], "PrevisaoClimatica"] = TTLCache(
    maxsize=10_000, ttl=CACHE_TTL_SEGUNDOS
)
_contador_chamadas_reais = 0


class FontePrevisaoIndisponivelError(Exception):
    """Levantada quando a Open-Meteo não responde em TIMEOUT_SEGUNDOS ou retorna erro."""


class PrevisaoClimatica(BaseModel):
    latitude: float
    longitude: float
    vento_10m_kmh: float
    vento_100m_kmh: float
    evapotranspiracao_mm: float
    umidade_solo_0_7cm: float
    umidade_solo_outras_camadas: dict[str, float]
    obtido_em_utc: datetime


def _chave_cache(latitude: float, longitude: float) -> tuple[float, float, str]:
    hora_atual = datetime.now(UTC).strftime("%Y-%m-%dT%H")
    return (round(latitude, 2), round(longitude, 2), hora_atual)


def contador_chamadas_reais() -> int:
    """Exposto para observabilidade do volume diário de chamadas (research.md)."""
    return _contador_chamadas_reais


async def obter_previsao(latitude: float, longitude: float) -> PrevisaoClimatica:
    """RF006/RF007 — previsão de vento (10m/100m), ET0 e umidade do solo em 4 profundidades."""
    global _contador_chamadas_reais

    chave = _chave_cache(latitude, longitude)
    if chave in _cache:
        return _cache[chave]

    parametros = {
        "latitude": latitude,
        "longitude": longitude,
        "hourly": "wind_speed_10m,wind_speed_100m",
        "daily": "et0_fao_evapotranspiration",
        "hourly_soil": "soil_moisture_0_to_7cm,soil_moisture_7_to_28cm",
        "timezone": "UTC",
    }
    try:
        async with httpx.AsyncClient(timeout=TIMEOUT_SEGUNDOS) as client:
            resposta = await client.get(OPENMETEO_URL, params=parametros)
            resposta.raise_for_status()
            corpo = resposta.json()
    except (httpx.TimeoutException, httpx.HTTPError) as exc:
        raise FontePrevisaoIndisponivelError(
            f"Open-Meteo indisponível para ({latitude}, {longitude})"
        ) from exc

    _contador_chamadas_reais += 1
    previsao = _parsear_resposta(latitude, longitude, corpo)
    _cache[chave] = previsao
    return previsao


def _parsear_resposta(latitude: float, longitude: float, corpo: dict) -> PrevisaoClimatica:
    hourly = corpo.get("hourly", {})
    hourly_soil = corpo.get("hourly_soil", corpo.get("hourly", {}))
    daily = corpo.get("daily", {})

    vento_10m = _primeiro_valor(hourly.get("wind_speed_10m"))
    vento_100m = _primeiro_valor(hourly.get("wind_speed_100m"))
    et0 = _primeiro_valor(daily.get("et0_fao_evapotranspiration"))
    umidade_0_7cm = _primeiro_valor(hourly_soil.get("soil_moisture_0_to_7cm"))

    outras_camadas = {
        chave: _primeiro_valor(valores)
        for chave, valores in hourly_soil.items()
        if chave != "soil_moisture_0_to_7cm"
    }

    return PrevisaoClimatica(
        latitude=latitude,
        longitude=longitude,
        vento_10m_kmh=vento_10m,
        vento_100m_kmh=vento_100m,
        evapotranspiracao_mm=et0,
        umidade_solo_0_7cm=umidade_0_7cm,
        umidade_solo_outras_camadas=outras_camadas,
        obtido_em_utc=datetime.now(UTC),
    )


def _primeiro_valor(lista: list | None) -> float:
    if not lista:
        return 0.0
    return float(lista[0])
