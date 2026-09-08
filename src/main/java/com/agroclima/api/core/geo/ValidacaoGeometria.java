package com.agroclima.api.core.geo;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Validacao de geometria pura (sem I/O, sem PostGIS) -- porte literal de
 * app/core/geo/validacao_geometria.py. RN015 (sobreposicao) NAO mora aqui, fica no
 * TalhaoService (Fase 5), igual ao Python (endpoints/talhoes.py).
 */
public final class ValidacaoGeometria {

    public static final double PARA_LAT_MIN = -9.9;
    public static final double PARA_LAT_MAX = 2.6;
    public static final double PARA_LON_MIN = -59.0;
    public static final double PARA_LON_MAX = -46.0;

    private ValidacaoGeometria() {}

    /** RN016 -- heuristica de confirmacao, nunca bloqueio duro. */
    public static boolean estaDentroDoPara(Point centroide) {
        double lat = centroide.getY();
        double lon = centroide.getX();
        return lat >= PARA_LAT_MIN && lat <= PARA_LAT_MAX && lon >= PARA_LON_MIN && lon <= PARA_LON_MAX;
    }

    public static boolean geometriaValida(Geometry geometria) {
        if (!geometria.isValid() || geometria.isEmpty()) {
            return false;
        }
        if (geometria instanceof Polygon poligono) {
            return poligono.getExteriorRing().getNumPoints() >= 4;
        }
        if (geometria instanceof MultiPolygon multiPoligono) {
            if (multiPoligono.getNumGeometries() == 0) {
                return false;
            }
            for (int i = 0; i < multiPoligono.getNumGeometries(); i++) {
                Polygon parte = (Polygon) multiPoligono.getGeometryN(i);
                if (parte.getExteriorRing().getNumPoints() < 4) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
