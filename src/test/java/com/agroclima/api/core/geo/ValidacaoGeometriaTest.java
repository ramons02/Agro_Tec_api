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
    void cidadesDoParaEstaoDentro() {
        // Belem, Maraba, Santarem e Altamira.
        for (double[] c : new double[][] {{-48.5, -1.45}, {-49.13, -5.38}, {-54.70, -2.44}, {-52.21, -3.20}}) {
            assertThat(ValidacaoGeometria.estaDentroDoPara(factory.createPoint(new Coordinate(c[0], c[1]))))
                    .as("ponto %s,%s", c[0], c[1]).isTrue();
        }
    }

    @Test
    void cidadesDeOutrosEstadosEstaoFora() {
        // Sao Paulo, Macapa (AP), Manaus (AM), Imperatriz (MA), Araguaina (TO), Boa Vista (RR), Cuiaba (MT).
        // Imperatriz e Araguaina cabiam na antiga caixa retangular do Para; pelo limite oficial nao cabem.
        double[][] fora = {{-46.6, -23.5}, {-51.07, 0.03}, {-60.02, -3.12}, {-47.48, -5.52},
                {-48.20, -7.19}, {-60.67, 2.82}, {-56.10, -15.60}};
        for (double[] c : fora) {
            assertThat(ValidacaoGeometria.estaDentroDoPara(factory.createPoint(new Coordinate(c[0], c[1]))))
                    .as("ponto %s,%s", c[0], c[1]).isFalse();
        }
    }

    @Test
    void talhaoQueAtravessaADivisaNaoEAceito() {
        // De Belem ate Macapa: comeca no Para e termina no Amapa.
        Polygon atravessa = factory.createPolygon(new Coordinate[] {
            new Coordinate(-48.6, -1.5), new Coordinate(-51.0, -1.5), new Coordinate(-51.0, 0.1),
            new Coordinate(-48.6, 0.1), new Coordinate(-48.6, -1.5)});
        assertThat(ValidacaoGeometria.estaDentroDoPara(atravessa)).isFalse();
    }

    @Test
    void talhaoPequenoNoInteriorDoParaEAceito() {
        assertThat(ValidacaoGeometria.estaDentroDoPara(quadrado(-48.50, -1.45, 0.01))).isTrue();
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
