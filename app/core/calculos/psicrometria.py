"""Cálculo de bulbo úmido via aproximação de Stull (2011), usado pelo Delta T
da pulverização (RN021/RN022, Escopo V3) — `calculos-geo-metero.md` §2.

Stull, R. (2011). "Wet-Bulb Temperature from Relative Humidity and Air
Temperature." Journal of Applied Meteorology and Climatology, 50(11), 2267-2269.
Aproximação empírica válida para condições atmosféricas normais.
"""

import math


def calcular_bulbo_umido(temperatura_seca_c: float, umidade_relativa_pct: float) -> float:
    """T_w — temperatura de bulbo úmido em °C, a partir da temperatura de
    bulbo seco (°C) e da umidade relativa (%), sem exigir sensor dedicado."""
    t = temperatura_seca_c
    rh = umidade_relativa_pct
    return (
        t * math.atan(0.151977 * math.sqrt(rh + 8.313659))
        + math.atan(t + rh)
        - math.atan(rh - 1.676331)
        + 0.00391838 * rh**1.5 * math.atan(0.023101 * rh)
        - 4.686035
    )


def calcular_delta_t(temperatura_seca_c: float, umidade_relativa_pct: float) -> float:
    """Delta T = T_seca - T_úmida (RN021/RN022)."""
    bulbo_umido = calcular_bulbo_umido(temperatura_seca_c, umidade_relativa_pct)
    return temperatura_seca_c - bulbo_umido
