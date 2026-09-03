"""Cálculo puro da Recomendação Acionável de "Próximo Passo" (feature 012,
RN011-RN013/RN019), portado de `recomendacao.ts` do protótipo (já validado
com o dono do produto em 2026-09-03). Combina status de plantio (feature 011)
e status de pulverização (feature 009) num texto único + prioridade — função
pura, sem I/O, para ser auditável (FR-008)."""

import enum
from dataclasses import dataclass

from app.core.calculos.pulverizacao import ClassificacaoPulverizacao
from app.core.calculos.status_plantio import StatusPlantio

LIMITE_TENDENCIA_PP = 1.5  # RN019 — pontos percentuais de CAD em 3 dias

AVISO_FIXO = "Sugestão gerada automaticamente — não substitui avaliação agronômica profissional."


class Prioridade(enum.StrEnum):
    ALTA = "ALTA"
    MEDIA = "MEDIA"
    BAIXA = "BAIXA"


class TendenciaUmidade(enum.StrEnum):
    SUBINDO = "SUBINDO"
    CAINDO = "CAINDO"
    ESTAVEL = "ESTAVEL"


@dataclass
class Recomendacao:
    texto: str
    prioridade: Prioridade
    aviso: str = AVISO_FIXO


def calcular_tendencia_umidade(
    armazenamento_hoje_mm: float, armazenamento_3dias_atras_mm: float, cad_mm: float
) -> TendenciaUmidade:
    """RN019 — diferença de armazenamento (como % da CAD) entre hoje e 3 dias
    atrás; limiar de 1,5 p.p. é decisão de UX validada com o dono do produto,
    não um cálculo agronômico (ver research.md)."""
    if cad_mm <= 0:
        return TendenciaUmidade.ESTAVEL

    diferenca_pp = (armazenamento_hoje_mm - armazenamento_3dias_atras_mm) / cad_mm * 100
    if diferenca_pp >= LIMITE_TENDENCIA_PP:
        return TendenciaUmidade.SUBINDO
    if diferenca_pp <= -LIMITE_TENDENCIA_PP:
        return TendenciaUmidade.CAINDO
    return TendenciaUmidade.ESTAVEL


_TEXTO_AMARELO_POR_TENDENCIA = {
    TendenciaUmidade.SUBINDO: (
        "Solo em nível de atenção e melhorando — monitore antes de decidir o plantio."
    ),
    TendenciaUmidade.CAINDO: (
        "Solo em nível de atenção e piorando — a janela de plantio pode fechar em breve."
    ),
    TendenciaUmidade.ESTAVEL: (
        "Solo em nível de atenção, estável — vale reavaliar antes de decidir o plantio."
    ),
}

_CLAUSULA_PULVERIZACAO = {
    ClassificacaoPulverizacao.FAVORAVEL: "Janela de pulverização liberada agora.",
    ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE: (
        "Pulverização bloqueada por vento forte — aguarde a próxima checagem."
    ),
    ClassificacaoPulverizacao.BLOQUEIO_INVERSAO_TERMICA: (
        "Pulverização bloqueada por inversão térmica — não aplique defensivos até a condição normalizar."
    ),
    ClassificacaoPulverizacao.BLOQUEIO_EVAPORACAO_EXCESSIVA: (
        "Pulverização bloqueada por evaporação excessiva — a calda pode não atingir o alvo."
    ),
}

_BLOQUEIOS_PULVERIZACAO = {
    ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE,
    ClassificacaoPulverizacao.BLOQUEIO_INVERSAO_TERMICA,
    ClassificacaoPulverizacao.BLOQUEIO_EVAPORACAO_EXCESSIVA,
}


def _texto_plantio(status_plantio: StatusPlantio | None, tendencia_umidade: TendenciaUmidade) -> str:
    if status_plantio is None:
        return "Ainda sem balanço hídrico calculado para este talhão."
    if status_plantio == StatusPlantio.VERMELHO:
        return "Solo em risco crítico — evite tráfego de maquinário pesado até a umidade se recuperar."
    if status_plantio == StatusPlantio.AMARELO:
        return _TEXTO_AMARELO_POR_TENDENCIA[tendencia_umidade]
    return "Solo em condição ideal para plantio — sem restrições hídricas no momento."


def gerar_recomendacao(
    status_plantio: StatusPlantio | None,
    status_pulverizacao: ClassificacaoPulverizacao | None,
    tendencia_umidade: TendenciaUmidade = TendenciaUmidade.ESTAVEL,
) -> Recomendacao:
    """FR-001-FR-007 — RN011: Vermelho é sempre ALTA, independente da
    pulverização. RN012: Amarelo OU pulverização bloqueada (qualquer motivo)
    é MEDIA. RN019: tendência só entra no texto quando o status é Amarelo
    (FR-005)."""
    texto = _texto_plantio(status_plantio, tendencia_umidade)

    pulverizacao_bloqueada = status_pulverizacao in _BLOQUEIOS_PULVERIZACAO
    if status_pulverizacao is not None:
        texto = f"{texto} {_CLAUSULA_PULVERIZACAO[status_pulverizacao]}"
    else:
        texto = f"{texto} Sem dado de pulverização disponível no momento."

    if status_plantio == StatusPlantio.VERMELHO:
        prioridade = Prioridade.ALTA
    elif status_plantio == StatusPlantio.AMARELO or pulverizacao_bloqueada:
        prioridade = Prioridade.MEDIA
    else:
        prioridade = Prioridade.BAIXA

    return Recomendacao(texto=texto, prioridade=prioridade)
