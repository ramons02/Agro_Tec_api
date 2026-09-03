"""Cálculos puros de pulverização (RN001-RN003, `calculos-geo-metero.md` §2).

`converter_ms_para_kmh` é usado já pela feature 008 (conversão da unidade
nativa do INMET, m/s, para km/h antes de servir ao cliente); a classificação
de janela de pulverização (feature 009) é portada de `regrasPulverizacao.ts`
do protótipo.
"""

import enum

FATOR_MS_PARA_KMH = 3.6

LIMITE_VENTO_MIN_FAVORAVEL = 3.0
LIMITE_VENTO_MAX_FAVORAVEL = 10.0
LIMITE_RAJADA_MAX_FAVORAVEL = 15.0

# Escopo V3 (RN021/RN022) — checagem complementar à de vento, não substituta.
LIMITE_DELTA_T_MIN_FAVORAVEL = 2.0
LIMITE_DELTA_T_MAX_FAVORAVEL = 10.0


class ClassificacaoPulverizacao(enum.StrEnum):
    FAVORAVEL = "FAVORAVEL"
    BLOQUEIO_VENTO_FORTE = "BLOQUEIO_VENTO_FORTE"
    BLOQUEIO_INVERSAO_TERMICA = "BLOQUEIO_INVERSAO_TERMICA"
    BLOQUEIO_EVAPORACAO_EXCESSIVA = "BLOQUEIO_EVAPORACAO_EXCESSIVA"  # Escopo V3, Delta T > 10°C


def converter_ms_para_kmh(velocidade_ms: float) -> float:
    """v_km/h = v_m/s × 3,6 (calculos-geo-metero.md §2)."""
    return velocidade_ms * FATOR_MS_PARA_KMH


def classificar_pulverizacao(vento_kmh: float, rajada_kmh: float) -> ClassificacaoPulverizacao:
    """RN001-RN003 — ordem de checagem idêntica a `regrasPulverizacao.ts`
    (protótipo, já validado): vento forte/rajada é checado ANTES de inversão
    térmica. Isso importa no caso de borda vento<3 e rajada>15 simultâneos —
    as duas condições se sobrepõem e a regra oficial não define precedência
    explicitamente; mantemos a mesma prioridade já validada com o cliente no
    protótipo (vento forte prevalece) em vez de inventar uma nova ordem."""
    if vento_kmh > LIMITE_VENTO_MAX_FAVORAVEL or rajada_kmh > LIMITE_RAJADA_MAX_FAVORAVEL:
        return ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE
    if vento_kmh < LIMITE_VENTO_MIN_FAVORAVEL:
        return ClassificacaoPulverizacao.BLOQUEIO_INVERSAO_TERMICA
    return ClassificacaoPulverizacao.FAVORAVEL


def classificar_delta_t(delta_t_c: float) -> ClassificacaoPulverizacao:
    """RN021/RN022 (Escopo V3) — checagem complementar à de vento
    (`classificar_pulverizacao`), não a substitui. A pulverização final só é
    FAVORÁVEL se ambas as classificações (vento e Delta T) forem FAVORÁVEL."""
    if delta_t_c > LIMITE_DELTA_T_MAX_FAVORAVEL:
        return ClassificacaoPulverizacao.BLOQUEIO_EVAPORACAO_EXCESSIVA
    if delta_t_c < LIMITE_DELTA_T_MIN_FAVORAVEL:
        return ClassificacaoPulverizacao.BLOQUEIO_INVERSAO_TERMICA
    return ClassificacaoPulverizacao.FAVORAVEL
