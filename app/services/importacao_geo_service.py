"""Extrai o primeiro polígono válido de um arquivo GeoJSON, KML ou Shapefile
(FR-005). Suporte a Shapefile e KML é limitado ao necessário para localizar um
Polygon — não valida todos os recursos desses formatos.
"""

import io
import json
import xml.etree.ElementTree as ET
import zipfile

import shapefile  # pyshp
from shapely.geometry import Polygon, shape

_KML_NS = {"kml": "http://www.opengis.net/kml/2.2"}


class ArquivoGeoInvalidoError(Exception):
    """Levantada quando nenhum polígono válido é encontrado no arquivo."""


def extrair_primeiro_poligono(nome_arquivo: str, conteudo: bytes) -> Polygon:
    extensao = nome_arquivo.lower().rsplit(".", maxsplit=1)[-1]
    if extensao in ("geojson", "json"):
        return _extrair_de_geojson(conteudo)
    if extensao == "kml":
        return _extrair_de_kml(conteudo)
    if extensao in ("shp", "zip"):
        return _extrair_de_shapefile(conteudo, zipado=extensao == "zip")
    raise ArquivoGeoInvalidoError(f"Formato não suportado: .{extensao}")


def _extrair_de_geojson(conteudo: bytes) -> Polygon:
    dados = json.loads(conteudo)
    tipo = dados.get("type")

    if tipo == "FeatureCollection":
        for feature in dados.get("features", []):
            poligono = _geometria_para_poligono(feature.get("geometry", {}))
            if poligono is not None:
                return poligono
    elif tipo == "Feature":
        poligono = _geometria_para_poligono(dados.get("geometry", {}))
        if poligono is not None:
            return poligono
    else:
        poligono = _geometria_para_poligono(dados)
        if poligono is not None:
            return poligono

    raise ArquivoGeoInvalidoError("Nenhum polígono válido encontrado no GeoJSON.")


def _geometria_para_poligono(geometria: dict) -> Polygon | None:
    if geometria.get("type") == "Polygon":
        return shape(geometria)
    if geometria.get("type") == "MultiPolygon":
        multi = shape(geometria)
        return multi.geoms[0] if len(multi.geoms) > 0 else None
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


def _extrair_de_shapefile(conteudo: bytes, zipado: bool) -> Polygon:
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
        poligono = shape(forma.__geo_interface__)
        if poligono.geom_type == "Polygon":
            return poligono
        if poligono.geom_type == "MultiPolygon" and len(poligono.geoms) > 0:
            return poligono.geoms[0]

    raise ArquivoGeoInvalidoError("Nenhum polígono válido encontrado no Shapefile.")
