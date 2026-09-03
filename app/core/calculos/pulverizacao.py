"""Cálculos puros de pulverização (RN001-RN003, `calculos-geo-metero.md` §2).

`converter_ms_para_kmh` é usado já pela feature 008 (conversão da unidade
nativa do INMET, m/s, para km/h antes de servir ao cliente); a classificação
de janela de pulverização em si é adicionada pela feature 009.
"""

FATOR_MS_PARA_KMH = 3.6


def converter_ms_para_kmh(velocidade_ms: float) -> float:
    """v_km/h = v_m/s × 3,6 (calculos-geo-metero.md §2)."""
    return velocidade_ms * FATOR_MS_PARA_KMH
