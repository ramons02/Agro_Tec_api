"""Cálculos puros de solo (RD005, RN020) — sem I/O, testáveis isoladamente."""

from app.db.models.talhao import TipoSolo

PROFUNDIDADE_RAIZES_PADRAO_MM = 200.0  # exemplo oficial de calculos-geo-metero.md §4A

LIMIAR_ARGILA_ARGILOSO_PCT = 35.0
LIMIAR_AREIA_ARENOSO_PCT = 70.0
LIMIAR_ARGILA_MAX_PARA_ARENOSO_PCT = 15.0


def classificar_textura(argila_pct: float, areia_pct: float, silte_pct: float) -> TipoSolo:
    """Limiares simplificados dos pontos de corte do triângulo textural USDA
    (ver research.md) — três categorias (RD005), não as 12 classes completas."""
    if argila_pct >= LIMIAR_ARGILA_ARGILOSO_PCT:
        return TipoSolo.ARGILOSO
    if areia_pct >= LIMIAR_AREIA_ARENOSO_PCT and argila_pct < LIMIAR_ARGILA_MAX_PARA_ARENOSO_PCT:
        return TipoSolo.ARENOSO
    return TipoSolo.MISTO


def calcular_cad(
    capacidade_campo_pct: float,
    ponto_murcha_permanente_pct: float,
    densidade_solo_g_cm3: float,
    profundidade_raizes_mm: float = PROFUNDIDADE_RAIZES_PADRAO_MM,
) -> float:
    """RN020 — CAD = (CC - PMP) × ρs × z, com CC/PMP em fração (0-1) de retenção."""
    cc_fracao = capacidade_campo_pct / 100
    pmp_fracao = ponto_murcha_permanente_pct / 100
    return (cc_fracao - pmp_fracao) * densidade_solo_g_cm3 * profundidade_raizes_mm


def estimar_cc_pmp(
    fracao_argila_pct: float, fracao_areia_pct: float, materia_organica_pct: float
) -> tuple[float, float]:
    """Estima Capacidade de Campo (CC, θ a -33kPa) e Ponto de Murcha Permanente
    (PMP, θ a -1500kPa), em % de retenção, via função de pedotransferência de
    Saxton & Rawls (1986) — "Estimation of Soil Water Properties".

    NOTA IMPORTANTE: o SoilGrids não retorna CC/PMP diretamente (só textura,
    matéria orgânica e densidade) — a spec/data-model original da feature 004
    assumia isso incorretamente. Coeficientes reproduzidos de memória, sem
    acesso à publicação original nesta sessão para conferência linha a linha.
    **Recomendado validar com um agrônomo/pedólogo antes de decisões reais de
    plantio baseadas neste valor** — ver Assumptions em
    Agro_Tec_documentacao/specs/004-solo-soilgrids/spec.md.
    """
    areia_fracao = fracao_areia_pct / 100
    argila_fracao = fracao_argila_pct / 100

    pmp_bruto = (
        -0.024 * areia_fracao
        + 0.487 * argila_fracao
        + 0.006 * materia_organica_pct
        + 0.005 * (areia_fracao * materia_organica_pct)
        - 0.013 * (argila_fracao * materia_organica_pct)
        + 0.068 * (areia_fracao * argila_fracao)
        + 0.031
    )
    pmp_fracao = pmp_bruto + (0.14 * pmp_bruto - 0.02)

    cc_bruto = (
        -0.251 * areia_fracao
        + 0.195 * argila_fracao
        + 0.011 * materia_organica_pct
        + 0.006 * (areia_fracao * materia_organica_pct)
        - 0.027 * (argila_fracao * materia_organica_pct)
        + 0.452 * (areia_fracao * argila_fracao)
        + 0.299
    )
    cc_fracao = cc_bruto + (1.283 * cc_bruto**2 - 0.374 * cc_bruto - 0.015)

    return cc_fracao * 100, max(pmp_fracao, 0.0) * 100
