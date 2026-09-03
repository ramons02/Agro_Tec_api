"""Cálculo puro do status de plantio (RN004-RN006), portado de
`balancoHidrico.ts::classificarStatusPlantio` do protótipo (já validado).
"""

import enum

LIMIAR_VERMELHO_SECA_PCT = 0.30
LIMIAR_VERMELHO_ENCHARCAMENTO_PCT = 0.95
LIMIAR_VERDE_MIN_PCT = 0.60
LIMIAR_VERDE_MAX_PCT = 0.90
CHUVA_PREVISTA_MINIMA_VERDE_MM = 5.0


class StatusPlantio(enum.StrEnum):
    VERDE = "VERDE"
    AMARELO = "AMARELO"
    VERMELHO = "VERMELHO"


def classificar_status(
    armazenamento_mm: float, cad_mm: float, chuva_prevista_mm: float
) -> StatusPlantio:
    """RN004-RN006 — Amarelo é o fallback conservador para toda faixa
    intermediária que a matriz oficial deixa implícita (90-95% CAD; e
    60-90% CAD sem os 5mm de chuva prevista), nunca Vermelho por omissão."""
    percentual_cad = armazenamento_mm / cad_mm

    if percentual_cad < LIMIAR_VERMELHO_SECA_PCT or percentual_cad > LIMIAR_VERMELHO_ENCHARCAMENTO_PCT:
        return StatusPlantio.VERMELHO
    if (
        LIMIAR_VERDE_MIN_PCT <= percentual_cad <= LIMIAR_VERDE_MAX_PCT
        and chuva_prevista_mm >= CHUVA_PREVISTA_MINIMA_VERDE_MM
    ):
        return StatusPlantio.VERDE
    return StatusPlantio.AMARELO
