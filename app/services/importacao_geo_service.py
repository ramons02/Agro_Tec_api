"""Extrai a geometria (Polygon ou MultiPolygon) de um arquivo GeoJSON, KML ou
Shapefile (FR-005). Suporte a Shapefile e KML é limitado ao necessário para
localizar uma geometria poligonal — não valida todos os recursos desses
formatos.

Escopo V3 (2026-09-03): mantém `MultiPolygon` completo (todas as partes) em
vez de reduzir para a primeira parte, já que `Talhao.geometria` agora aceita
múltiplas partes desconexas.
"""

import io
import json
import xml.etree.ElementTree as ET
import zipfile

import shapefile  # pyshp
from shapely.geometry import MultiPolygon, Polygon, shape
from shapely.geometry.base import BaseGeometry

_KML_NS = {"kml": "http://www.opengis.net/kml/2.2"}


class ArquivoGeoInvalidoError(Exception):
    """Levantada quando nenhuma geometria poligonal válida é encontrada no arquivo."""


def extrair_geometria(nome_arquivo: str, conteudo: bytes) -> BaseGeometry:
    extensao = nome_arquivo.lower().rsplit(".", maxsplit=1)[-1]
    if extensao in ("geojson", "json"):
        return _extrair_de_geojson(conteudo)
    if extensao == "kml":
        return _extrair_de_kml(conteudo)
    if extensao in ("shp", "zip"):
        return _extrair_de_shapefile(conteudo, zipado=extensao == "zip")
    raise ArquivoGeoInvalidoError(f"Formato não suportado: .{extensao}")


def _extrair_de_geojson(conteudo: bytes) -> BaseGeometry:
    dados = json.loads(conteudo)
    tipo = dados.get("type")

    if tipo == "FeatureCollection":
        for feature in dados.get("features", []):
            geometria = _geometria_poligonal(feature.get("geometry", {}))
            if geometria is not None:
                return geometria
    elif tipo == "Feature":
        geometria = _geometria_poligonal(dados.get("geometry", {}))
        if geometria is not None:
            return geometria
    else:
        geometria = _geometria_poligonal(dados)
        if geometria is not None:
            return geometria

    raise ArquivoGeoInvalidoError("Nenhuma geometria poligonal válida encontrada no GeoJSON.")


def _geometria_poligonal(geometria: dict) -> BaseGeometry | None:
    if geometria.get("type") in ("Polygon", "MultiPolygon"):
        return shape(geometria)
    return None


def _extrair_de_kml(conteudo: bytes) -> Polygon:
    raiz = ET.fromstring(conteudo)
    for coordenadas_el in raiz.iter("{http://www.opengis.net/kml/2.2}coordinates"):
        texto = (coordenadas_el.text or "").strip()
        if not texto:
            continue
        pontos = []
        for par in texto.split():
            partes = par.split(",")
            if len(partes) >= 2:
                pontos.append((float(partes[0]), float(partes[1])))
        if len(pontos) >= 4:
            return Polygon(pontos)

    raise ArquivoGeoInvalidoError("Nenhum polígono válido encontrado no KML.")


def _extrair_de_shapefile(conteudo: bytes, zipado: bool) -> BaseGeometry:
    if zipado:
        with zipfile.ZipFile(io.BytesIO(conteudo)) as arquivo_zip:
            nome_shp = next(n for n in arquivo_zip.namelist() if n.lower().endswith(".shp"))
            base = nome_shp.rsplit(".", 1)[0]
            leitor = shapefile.Reader(
                shp=io.BytesIO(arquivo_zip.read(f"{base}.shp")),
                shx=io.BytesIO(arquivo_zip.read(f"{base}.shx")),
                dbf=io.BytesIO(arquivo_zip.read(f"{base}.dbf")),
            )
    else:
        leitor = shapefile.Reader(shp=io.BytesIO(conteudo))

    for forma in leitor.shapes():
        geometria = shape(forma.__geo_interface__)
        if geometria.geom_type in ("Polygon", "MultiPolygon"):
            return geometria

    raise ArquivoGeoInvalidoError("Nenhum polígono válido encontrado no Shapefile.")


def normalizar_para_multipolygon(geometria: BaseGeometry) -> MultiPolygon:
    """`Talhao.geometria`/`Propriedade.geometria` são `MultiPolygon` (Escopo V3)
    — normaliza um `Polygon` isolado (ex.: desenhado manualmente ou vindo de um
    GeoJSON simples) para `MultiPolygon` de uma parte só antes de persistir."""
    if geometria.geom_type == "MultiPolygon":
        return geometria
    if geometria.geom_type == "Polygon":
        return MultiPolygon([geometria])
    raise ArquivoGeoInvalidoError(f"Tipo de geometria não suportado: {geometria.geom_type}")
