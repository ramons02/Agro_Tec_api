import json
import zipfile
from io import BytesIO

import pytest
import shapefile

from app.services.importacao_geo_service import (
    ArquivoGeoInvalidoError,
    extrair_geometria,
    normalizar_para_multipolygon,
)

POLIGONO_GEOJSON = {
    "type": "Polygon",
    "coordinates": [[[-48.50, -1.46], [-48.49, -1.46], [-48.49, -1.45], [-48.50, -1.45], [-48.50, -1.46]]],
}

MULTIPOLIGONO_GEOJSON = {
    "type": "MultiPolygon",
    "coordinates": [
        [[[-48.50, -1.46], [-48.49, -1.46], [-48.49, -1.45], [-48.50, -1.45], [-48.50, -1.46]]],
        [[[-48.40, -1.40], [-48.39, -1.40], [-48.39, -1.39], [-48.40, -1.39], [-48.40, -1.40]]],
    ],
}


def test_extrai_poligono_de_geojson_polygon_puro():
    geometria = extrair_geometria("talhao.geojson", json.dumps(POLIGONO_GEOJSON).encode())
    assert geometria.geom_type == "Polygon"
    assert geometria.is_valid


def test_extrai_multipoligono_de_geojson_mantendo_todas_as_partes():
    geometria = extrair_geometria("talhao.geojson", json.dumps(MULTIPOLIGONO_GEOJSON).encode())
    assert geometria.geom_type == "MultiPolygon"
    assert len(geometria.geoms) == 2


def test_extrai_poligono_de_geojson_feature_collection():
    feature_collection = {
        "type": "FeatureCollection",
        "features": [{"type": "Feature", "properties": {}, "geometry": POLIGONO_GEOJSON}],
    }
    geometria = extrair_geometria("talhao.geojson", json.dumps(feature_collection).encode())
    assert geometria.geom_type == "Polygon"


def test_geojson_sem_poligono_levanta_erro():
    ponto = {
        "type": "FeatureCollection",
        "features": [{"type": "Feature", "geometry": {"type": "Point", "coordinates": [0, 0]}}],
    }
    with pytest.raises(ArquivoGeoInvalidoError):
        extrair_geometria("talhao.geojson", json.dumps(ponto).encode())


def test_extrai_poligono_de_kml():
    kml = """<?xml version="1.0" encoding="UTF-8"?>
    <kml xmlns="http://www.opengis.net/kml/2.2">
      <Document>
        <Placemark>
          <Polygon>
            <outerBoundaryIs>
              <LinearRing>
                <coordinates>
                  -48.50,-1.46,0 -48.49,-1.46,0 -48.49,-1.45,0 -48.50,-1.45,0 -48.50,-1.46,0
                </coordinates>
              </LinearRing>
            </outerBoundaryIs>
          </Polygon>
        </Placemark>
      </Document>
    </kml>"""
    geometria = extrair_geometria("talhao.kml", kml.encode())
    assert geometria.geom_type == "Polygon"
    assert geometria.is_valid


def test_kml_sem_coordenadas_levanta_erro():
    kml = '<?xml version="1.0"?><kml xmlns="http://www.opengis.net/kml/2.2"><Document/></kml>'
    with pytest.raises(ArquivoGeoInvalidoError):
        extrair_geometria("talhao.kml", kml.encode())


def _gerar_shapefile_zip() -> bytes:
    buffer_shp, buffer_shx, buffer_dbf = BytesIO(), BytesIO(), BytesIO()
    with shapefile.Writer(
        shp=buffer_shp, shx=buffer_shx, dbf=buffer_dbf, shapeType=shapefile.POLYGON
    ) as writer:
        writer.field("nome", "C")
        writer.poly(
            [[[-48.50, -1.46], [-48.49, -1.46], [-48.49, -1.45], [-48.50, -1.45], [-48.50, -1.46]]]
        )
        writer.record("Talhao Teste")

    zip_buffer = BytesIO()
    with zipfile.ZipFile(zip_buffer, "w") as arquivo_zip:
        arquivo_zip.writestr("talhao.shp", buffer_shp.getvalue())
        arquivo_zip.writestr("talhao.shx", buffer_shx.getvalue())
        arquivo_zip.writestr("talhao.dbf", buffer_dbf.getvalue())
    return zip_buffer.getvalue()


def test_extrai_poligono_de_shapefile_zipado():
    zip_bytes = _gerar_shapefile_zip()
    geometria = extrair_geometria("talhao.zip", zip_bytes)
    assert geometria.geom_type == "Polygon"
    assert geometria.is_valid


def test_formato_nao_suportado_levanta_erro():
    with pytest.raises(ArquivoGeoInvalidoError):
        extrair_geometria("talhao.txt", b"nao e um formato geo")


def test_normalizar_para_multipolygon_envolve_polygon_isolado():
    from shapely.geometry import shape

    poligono = shape(POLIGONO_GEOJSON)
    multipoligono = normalizar_para_multipolygon(poligono)
    assert multipoligono.geom_type == "MultiPolygon"
    assert len(multipoligono.geoms) == 1


def test_normalizar_para_multipolygon_mantem_multipolygon_existente():
    from shapely.geometry import shape

    multipoligono_original = shape(MULTIPOLIGONO_GEOJSON)
    resultado = normalizar_para_multipolygon(multipoligono_original)
    assert resultado.geom_type == "MultiPolygon"
    assert len(resultado.geoms) == 2
