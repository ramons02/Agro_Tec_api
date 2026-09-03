"""Cliente da API pública SoilGrids (ISRIC/Embrapa), REST v2.0.

NOTA: mesmo aviso da apitempo do INMET (`inmet_service.py`) — o formato de
resposta abaixo (`properties.layers[].unit_measure.d_factor`, `depths[].values.mean`)
é o schema historicamente documentado da API v2.0, sem acesso à rede neste
ambiente para confirmar ao vivo. Parsing defensivo (nunca quebra em campo
ausente); confirmar contra uma chamada real antes de produção.
"""

import logging
from dataclasses import dataclass

import httpx

logger = logging.getLogger(__name__)

SOILGRIDS_URL = "https://rest.isric.org/soilgrids/v2.0/properties/query"
TIMEOUT_SEGUNDOS = 5.0  # consulta pontual no cadastro, não sujeita ao SLA de 3s de tempo real
PROPRIEDADES = ["clay", "sand", "silt", "soc", "bdod"]
PROFUNDIDADE = "0-5cm"


class FonteSoloIndisponivelError(Exception):
    """Levantada em timeout/erro HTTP — distinto de "sem cobertura" (retorna None)."""


@dataclass
class PerfilSoloDTO:
    fracao_argila_pct: float
    fracao_areia_pct: float
    fracao_silte_pct: float
    materia_organica_pct: float
    densidade_solo_g_cm3: float


async def parametrizar_solo(latitude: float, longitude: float) -> PerfilSoloDTO | None:
    """FR-001/FR-002 — retorna None (sem cobertura) em vez de erro, para nunca
    bloquear o cadastro do talhão (FR-006/RN016)."""
    parametros = {
        "lon": longitude,
        "lat": latitude,
        "property": PROPRIEDADES,
        "depth": PROFUNDIDADE,
        "value": "mean",
    }
    try:
        async with httpx.AsyncClient(timeout=TIMEOUT_SEGUNDOS) as client:
            resposta = await client.get(SOILGRIDS_URL, params=parametros)
            resposta.raise_for_status()
    except (httpx.TimeoutException, httpx.HTTPError) as exc:
        raise FonteSoloIndisponivelError(
            f"SoilGrids indisponível para ({latitude}, {longitude})"
        ) from exc

    return _parsear_resposta(resposta.json())


def _valor_camada(layers: list[dict], nome: str) -> float | None:
    for layer in layers:
        if layer.get("name") != nome:
            continue
        d_factor = layer.get("unit_measure", {}).get("d_factor", 1) or 1
        for profundidade in layer.get("depths", []):
            media = profundidade.get("values", {}).get("mean")
            if media is not None:
                return media / d_factor
    return None


def _parsear_resposta(corpo: dict) -> PerfilSoloDTO | None:
    layers = corpo.get("properties", {}).get("layers", [])
    if not layers:
        return None  # sem cobertura para a coordenada (FR-006)

    argila = _valor_camada(layers, "clay")
    areia = _valor_camada(layers, "sand")
    silte = _valor_camada(layers, "silt")
    materia_organica = _valor_camada(layers, "soc")
    densidade = _valor_camada(layers, "bdod")

    if argila is None or areia is None or silte is None:
        return None

    return PerfilSoloDTO(
        fracao_argila_pct=argila / 10,  # g/kg -> %
        fracao_areia_pct=areia / 10,
        fracao_silte_pct=silte / 10,
        materia_organica_pct=(materia_organica or 0) / 10,
        densidade_solo_g_cm3=densidade / 100 if densidade is not None else 1.3,
    )
