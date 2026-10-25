package com.agroclima.api.core.geo;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Validacao de geometria pura (sem I/O, sem PostGIS) -- porte literal de
 * app/core/geo/validacao_geometria.py. RN015 (sobreposicao) NAO mora aqui, fica no
 * TalhaoService (Fase 5), igual ao Python (endpoints/talhoes.py).
 */
public final class ValidacaoGeometria {

    /**
     * Tolerancia sobre o limite do Para, em graus (~1,1 km). O limite oficial do IBGE e generalizado
     * (3,6 mil vertices para o estado inteiro), entao um talhao colado na divisa ou na margem de um
     * rio pode cair uns metros "fora" so por causa da simplificacao.
     */
    static final double TOLERANCIA_GRAUS = 0.01;

    private ValidacaoGeometria() {}

    /** Carrega o limite uma vez, na primeira consulta (carregar o arquivo nao pode atrasar a subida da API). */
    private static final class LimiteDoPara {
        static final PreparedGeometry LIMITE = carregar();

        private static PreparedGeometry carregar() {
            try (InputStream arquivo = ValidacaoGeometria.class.getResourceAsStream("/geo/para.geojson")) {
                if (arquivo == null) {
                    throw new IllegalStateException("Limite do Pará (geo/para.geojson) ausente do pacote.");
                }
                Geometry estado = GeoJsonUtil.parseGeometria(new String(arquivo.readAllBytes(), StandardCharsets.UTF_8));
                return PreparedGeometryFactory.prepare(estado.buffer(TOLERANCIA_GRAUS));
            } catch (IOException ex) {
                throw new IllegalStateException("Falha ao ler o limite do Pará.", ex);
            }
        }
    }

    /**
     * Regra de negocio (decisao do dono do produto, 2026-10-24): so vale talhao inteiramente dentro do
     * estado do Para, pelo limite oficial do IBGE, sem confirmacao que libere. Substitui a antiga
     * caixa retangular no centroide (RN016), que aceitava Amapa, Maranhao, Tocantins e Amazonas.
     */
    public static boolean estaDentroDoPara(Geometry geometria) {
        return LimiteDoPara.LIMITE.covers(geometria);
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
