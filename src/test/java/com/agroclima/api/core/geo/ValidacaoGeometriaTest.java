package com.agroclima.api.core.geo;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import static org.assertj.core.api.Assertions.assertThat;

class ValidacaoGeometriaTest {

    private final GeometryFactory factory = new GeometryFactory();

    private Polygon quadrado(double x0, double y0, double lado) {
        return factory.createPolygon(new Coordinate[] {
                new Coordinate(x0, y0),
                new Coordinate(x0 + lado, y0),
                new Coordinate(x0 + lado, y0 + lado),
                new Coordinate(x0, y0 + lado),
                new Coordinate(x0, y0),
        });
    }

    @Test
    void centroideDentroDoParaEAceito() {
        // Belem aproximado
        Point ponto = factory.createPoint(new Coordinate(-48.5, -1.45));
        assertThat(ValidacaoGeometria.estaDentroDoPara(ponto)).isTrue();
    }

    @Test
    void centroideForaDoParaENegado() {
        // Sao Paulo, bem fora da bbox do Para
        Point ponto = factory.createPoint(new Coordinate(-46.6, -23.5));
        assertThat(ValidacaoGeometria.estaDentroDoPara(ponto)).isFalse();
    }

    @Test
    void limitesDaBboxSaoInclusivos() {
        Point canto = factory.createPoint(new Coordinate(ValidacaoGeometria.PARA_LON_MIN, ValidacaoGeometria.PARA_LAT_MIN));
        assertThat(ValidacaoGeometria.estaDentroDoPara(canto)).isTrue();
    }

    @Test
    void poligonoValidoComQuatroPontosEAceito() {
        assertThat(ValidacaoGeometria.geometriaValida(quadrado(0, 0, 10))).isTrue();
    }

    @Test
    void poligonoVazioENegado() {
        Polygon vazio = factory.createPolygon();
        assertThat(ValidacaoGeometria.geometriaValida(vazio)).isFalse();
    }

    @Test
    void poligonoAutoIntersectanteENegado() {
        // "gravata borboleta" -- classico caso invalido detectado por isValid().
        Polygon bowtie = factory.createPolygon(new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(10, 10),
                new Coordinate(10, 0),
                new Coordinate(0, 10),
                new Coordinate(0, 0),
        });
        assertThat(ValidacaoGeometria.geometriaValida(bowtie)).isFalse();
    }

    @Test
    void multiPolygonComTodasAsPartesValidasEAceito() {
        MultiPolygon multi = factory.createMultiPolygon(new Polygon[] {quadrado(0, 0, 10), quadrado(20, 20, 5)});
        assertThat(ValidacaoGeometria.geometriaValida(multi)).isTrue();
    }

    @Test
    void multiPolygonSemNenhumaParteENegado() {
        MultiPolygon vazio = factory.createMultiPolygon(new Polygon[0]);
        assertThat(ValidacaoGeometria.geometriaValida(vazio)).isFalse();
    }

    @Test
    void pontoNaoEGeometriaValidaParaTalhao() {
        Point ponto = factory.createPoint(new Coordinate(0, 0));
        assertThat(ValidacaoGeometria.geometriaValida(ponto)).isFalse();
    }
}
