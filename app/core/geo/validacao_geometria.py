"""Validação geométrica pura (sem I/O e sem acesso ao banco).

A checagem de sobreposição entre talhões (RN015) usa PostGIS `ST_Overlaps`/
`ST_Intersection` diretamente no endpoint (feature 005, plan.md — Princípio IV
da Constituição: consultas espaciais sobre dado persistido usam operadores
nativos do PostGIS, nunca cálculo aproximado em aplicação). As funções abaixo
cobrem apenas validação que não depende de comparar contra dado já persistido.
"""

from shapely.geometry.base import BaseGeometry

# Bounding box aproximada do estado do Pará (WGS 84) — heurística de confirmação
# (RN016), nunca validação rígida: um talhão fora dela é aceito com confirmação.
PARA_LAT_MIN = -9.9
PARA_LAT_MAX = 2.6
PARA_LON_MIN = -59.0
PARA_LON_MAX = -46.0


def esta_dentro_do_para(centroide: BaseGeometry) -> bool:
    """RN016 — True se o centroide cai dentro da bounding box aproximada do Pará."""
    return (
        PARA_LAT_MIN <= centroide.y <= PARA_LAT_MAX
        and PARA_LON_MIN <= centroide.x <= PARA_LON_MAX
    )


def geometria_valida(geometria: BaseGeometry) -> bool:
    """Validação estrutural mínima: `Polygon` ou `MultiPolygon` (Escopo V3) não
    vazio, topologicamente simples, com pelo menos 3 vértices distintos por
    parte (mínimo exigido pelo protótipo/RI008)."""
    if not geometria.is_valid or geometria.is_empty:
        return False
    if geometria.geom_type == "Polygon":
        return len(geometria.exterior.coords) >= 4
    if geometria.geom_type == "MultiPolygon":
        return len(geometria.geoms) > 0 and all(
            len(parte.exterior.coords) >= 4 for parte in geometria.geoms
        )
    return False
