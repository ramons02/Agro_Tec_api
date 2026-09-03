"""Cálculos puros do Balanço Hídrico do Solo (RN007, `calculos-geo-metero.md` §4B)."""

KC_FASE_INICIAL = 0.4  # ver research.md — valor de referência FAO-56 para fase inicial
ARM_INICIAL_FRACAO_CAD = 0.70  # talhão sem histórico (ver research.md)


def calcular_armazenamento(
    arm_anterior_mm: float,
    precipitacao_mm: float,
    et0_mm: float,
    cad_mm: float,
    kc: float = KC_FASE_INICIAL,
) -> float:
    """RN007 — ARM_i = min(CAD, max(0, ARM_{i-1} + P_i - ET_i)), ET_i = ET0 × Kc."""
    et_real_mm = et0_mm * kc
    arm_bruto = arm_anterior_mm + precipitacao_mm - et_real_mm
    return min(cad_mm, max(0.0, arm_bruto))


def armazenamento_inicial(cad_mm: float) -> float:
    """Valor de partida para talhão sem histórico (research.md)."""
    return ARM_INICIAL_FRACAO_CAD * cad_mm
