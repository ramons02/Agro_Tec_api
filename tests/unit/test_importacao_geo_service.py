import json
import zipfile
from io import BytesIO

import pytest
import shapefile

from app.services.importacao_geo_service import (
    ArquivoGeoInvalidoError,
    extrair_primeiro_poligono,
)

POLIGONO_GEOJSON = {
    "type": "Polygon",
    "coordinates": [[[-48.50, -1.46], [-48.49, -1.46], [-48.49, -1.45], [-48.50, -1.45], [-48.50, -1.46]]],
}


def test_extrai_poligono_de_geojson_polygon_puro():
    poligono = extrair_primeiro_poligono("talhao.geojson", json.dumps(POLIGONO_GEOJSON).encode())
    assert poligono.geom_type == "Polygon"
    assert poligono.is_valid


def test_extrai_poligono_de_geojson_feature_collection():
    feature_collection = {
        "type": "FeatureCollection",
        "features": [{"type": "Feature", "properties": {}, "geometry": POLIGONO_GEOJSON}],
    }
    poligono = extrair_primeiro_poligono(
        "talhao.geojson", json.dumps(feature_collection).encode()
    )
    assert poligono.geom_type == "Polygon"


def test_geojson_sem_poligono_levanta_erro():
    ponto = {
        "type": "FeatureCollection",
        "features": [{"type": "Feature", "geometry": {"type": "Point", "coordinates": [0, 0]}}],
    }
    with pytest.raises(ArquivoGeoInvalidoError):
        extrair_primeiro_poligono("talhao.geojson", json.dumps(ponto).encode())


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
    poligono = extrair_primeiro_poligono("talhao.kml", kml.encode())
    assert poligono.geom_type == "Polygon"
    assert poligono.is_valid


def test_kml_sem_coordenadas_levanta_erro():
    kml = '<?xml version="1.0"?><kml xmlns="http://www.opengis.net/kml/2.2"><Document/></kml>'
    with pytest.raises(ArquivoGeoInvalidoError):
        extrair_primeiro_poligono("talhao.kml", kml.encode())


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
    poligono = extrair_primeiro_poligono("talhao.zip", zip_bytes)
    assert poligono.geom_type == "Polygon"
    assert poligono.is_valid


def test_formato_nao_suportado_levanta_erro():
    with pytest.raises(ArquivoGeoInvalidoError):
        extrair_primeiro_poligono("talhao.txt", b"nao e um formato geo")
