package com.agroclima.api.core.geo;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.Polygon;

/** Espelha normalizar_para_multipolygon() de app/services/importacao_geo_service.py. */
public final class GeometriaUtil {

    /** SRID 4326 (WGS84) -- o mesmo usado em toda coluna geometry do schema. */
    public static final GeometryFactory FACTORY_4326 = new GeometryFactory(new PrecisionModel(), 4326);

    private GeometriaUtil() {}

    public static MultiPolygon normalizarParaMultiPolygon(Geometry geometria) {
        if (geometria instanceof MultiPolygon multiPoligono) {
            return multiPoligono;
        }
        if (geometria instanceof Polygon poligono) {
            return FACTORY_4326.createMultiPolygon(new Polygon[] {poligono});
        }
        throw new GeometriaInvalidaException(
                "Geometria deve ser do tipo Polygon ou MultiPolygon, recebido: " + geometria.getGeometryType());
    }
}
