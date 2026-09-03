"""Cliente da API pública do INMET (apitempo.inmet.gov.br).

NOTA: o formato de resposta abaixo (`CD_ESTACAO`, `TEM_INS`, `VEN_VEL` etc.) é o
schema historicamente documentado da apitempo do INMET, usado como referência
por não haver acesso à rede neste ambiente de desenvolvimento para validar a
resposta ao vivo. Antes de operar em produção, confirmar contra uma chamada
real que os nomes de campo não mudaram — RF003/HU-02 exige robustez a isso,
por isso o parsing abaixo é defensivo (não quebra em campo ausente).
"""

import logging
from dataclasses import dataclass
from datetime import UTC, datetime

import httpx

logger = logging.getLogger(__name__)

INMET_BASE_URL = "https://apitempo.inmet.gov.br"
TIMEOUT_SEGUNDOS = 3.0  # RN009 — timeout aciona fallback (feature 003)


class FonteIndisponivelError(Exception):
    """Levantada quando o INMET não responde em TIMEOUT_SEGUNDOS ou retorna erro."""


@dataclass
class EstacaoInmetDTO:
    codigo: str
    nome: str
    estado: str
    latitude: float
    longitude: float


@dataclass
class MedicaoInmetDTO:
    estacao_codigo: str
    data_hora_utc: datetime
    precipitacao_mm: float | None
    temperatura_c: float | None
    umidade_pct: float | None
    vento_velocidade_ms: float | None
    vento_rajada_ms: float | None


async def buscar_estacoes_pa() -> list[EstacaoInmetDTO]:
    """Catálogo de estações automáticas (`T`), filtrado para o estado do Pará."""
    async with httpx.AsyncClient(timeout=TIMEOUT_SEGUNDOS) as client:
        try:
            resposta = await client.get(f"{INMET_BASE_URL}/estacoes/T")
            resposta.raise_for_status()
        except (httpx.TimeoutException, httpx.HTTPError) as exc:
            raise FonteIndisponivelError("Catálogo de estações do INMET indisponível") from exc

    estacoes = []
    for item in resposta.json():
        if item.get("SG_ESTADO") != "PA":
            continue
        try:
            estacoes.append(
                EstacaoInmetDTO(
                    codigo=item["CD_ESTACAO"],
                    nome=item.get("DC_NOME", item["CD_ESTACAO"]),
                    estado="PA",
                    latitude=float(item["VL_LATITUDE"]),
                    longitude=float(item["VL_LONGITUDE"]),
                )
            )
        except (KeyError, ValueError, TypeError):
            logger.warning("Estação INMET com dados incompletos ignorada: %s", item)

    return estacoes


async def buscar_medicao_recente(codigo_estacao: str) -> MedicaoInmetDTO | None:
    """Última medição horária disponível para a estação, ou None se não houver leitura válida."""
    async with httpx.AsyncClient(timeout=TIMEOUT_SEGUNDOS) as client:
        try:
            resposta = await client.get(f"{INMET_BASE_URL}/estacao/dados/{codigo_estacao}")
            resposta.raise_for_status()
        except (httpx.TimeoutException, httpx.HTTPError) as exc:
            raise FonteIndisponivelError(f"INMET indisponível para {codigo_estacao}") from exc

    registros = resposta.json()
    if not registros:
        return None

    return _parsear_medicao_mais_recente(codigo_estacao, registros)


def _parsear_medicao_mais_recente(
    codigo_estacao: str, registros: list[dict]
) -> MedicaoInmetDTO | None:
    def _instante(registro: dict) -> str:
        return f"{registro.get('DT_MEDICAO', '')}{registro.get('HR_MEDICAO', '')}"

    registros_ordenados = sorted(registros, key=_instante, reverse=True)
    for registro in registros_ordenados:
        if registro.get("TEM_INS") is None and registro.get("VEN_VEL") is None:
            continue  # hora sem nenhuma leitura publicada ainda

        data_hora = _parsear_data_hora(registro)
        if data_hora is None:
            continue

        return MedicaoInmetDTO(
            estacao_codigo=codigo_estacao,
            data_hora_utc=data_hora,
            precipitacao_mm=_para_float(registro.get("CHUVA")),
            temperatura_c=_para_float(registro.get("TEM_INS")),
            umidade_pct=_para_float(registro.get("UMD_INS")),
            vento_velocidade_ms=_para_float(registro.get("VEN_VEL")),
            vento_rajada_ms=_para_float(registro.get("VEN_RAJ")),
        )

    return None


def _parsear_data_hora(registro: dict) -> datetime | None:
    data = registro.get("DT_MEDICAO")
    hora = registro.get("HR_MEDICAO")
    if not data or hora is None:
        return None
    try:
        hora_str = str(hora).zfill(4)
        return datetime.strptime(f"{data} {hora_str}", "%Y-%m-%d %H%M").replace(tzinfo=UTC)
    except ValueError:
        return None


def _para_float(valor) -> float | None:
    if valor is None or valor == "":
        return None
    try:
        return float(valor)
    except (TypeError, ValueError):
        return None
