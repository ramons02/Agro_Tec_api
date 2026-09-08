package com.agroclima.api.core.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;

/** Espelha _extrair_de_geojson() de app/services/importacao_geo_service.py + ST_AsGeoJSON dos endpoints. */
public final class GeoJsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // SRID 4326 explicito -- sem isso a geometria nasce com SRID 0 e qualquer operador
    // espacial contra colunas persistidas (ST_Overlaps etc.) falha por SRID misto.
    private static final GeoJsonReader READER = new GeoJsonReader(GeometriaUtil.FACTORY_4326);
    private static final GeoJsonWriter WRITER = new GeoJsonWriter();

    private GeoJsonUtil() {}

    /** Aceita FeatureCollection (usa a 1a feature com Polygon/MultiPolygon), Feature ou geometria pura. */
    public static Geometry parseGeometria(String json) {
        JsonNode raiz;
        try {
            raiz = MAPPER.readTree(json);
        } catch (Exception ex) {
            throw new GeometriaInvalidaException("JSON de geometria inválido.");
        }
        JsonNode geometriaNode = extrairNoDeGeometria(raiz);
        try {
            return READER.read(geometriaNode.toString());
        } catch (ParseException ex) {
            throw new GeometriaInvalidaException("Geometria GeoJSON inválida.");
        }
    }

    private static JsonNode extrairNoDeGeometria(JsonNode raiz) {
        String tipo = raiz.path("type").asText("");
        if ("FeatureCollection".equals(tipo)) {
            for (JsonNode feature : raiz.path("features")) {
                JsonNode geometria = feature.path("geometry");
                String tipoGeom = geometria.path("type").asText("");
                if ("Polygon".equals(tipoGeom) || "MultiPolygon".equals(tipoGeom)) {
                    return geometria;
                }
            }
            throw new GeometriaInvalidaException("Nenhuma feature com geometria Polygon/MultiPolygon encontrada.");
        }
        if ("Feature".equals(tipo)) {
            return raiz.path("geometry");
        }
        return raiz;
    }

    public static String paraGeoJson(Geometry geometria) {
        return WRITER.write(geometria);
    }

    /** Como paraGeoJson(), mas ja parseado num JsonNode -- pra embutir como objeto aninhado, nunca string escapada. */
    public static JsonNode paraGeoJsonNode(Geometry geometria) {
        if (geometria == null) {
            return null;
        }
        try {
            return MAPPER.readTree(WRITER.write(geometria));
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao serializar geometria para GeoJSON.", ex);
        }
    }
}
