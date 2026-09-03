"""Interpolação IDW (Inverso do Quadrado da Distância), `calculos-geo-metero.md`
§1B (Escopo V3) — função pura, sem I/O.
"""


def interpolar_idw(valores_e_distancias_km: list[tuple[float, float]]) -> float:
    """Média ponderada por 1/d² entre até N estações (tipicamente 3).

    `valores_e_distancias_km`: lista de (valor_medido, distancia_km). Se a
    distância de algum item for 0 (estação sobre o centroide), retorna o valor
    dessa estação diretamente, sem interpolação (evita divisão por zero)."""
    if not valores_e_distancias_km:
        raise ValueError("lista de valores/distâncias vazia")

    for valor, distancia in valores_e_distancias_km:
        if distancia == 0:
            return valor

    numerador = sum(valor / (distancia**2) for valor, distancia in valores_e_distancias_km)
    denominador = sum(1 / (distancia**2) for _, distancia in valores_e_distancias_km)
    return numerador / denominador
