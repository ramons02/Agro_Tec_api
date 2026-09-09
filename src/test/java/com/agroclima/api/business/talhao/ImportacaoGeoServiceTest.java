package com.agroclima.api.business.talhao;

import com.agroclima.api.core.geo.GeometriaInvalidaException;
import org.geotools.api.data.SimpleFeatureStore;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Nenhum teste existia pra este arquivo antes (spec 019, User Story 2) -- os fixtures de
 * Shapefile sao gerados via GeoTools no proprio teste (nao ha nenhum .shp de exemplo
 * versionado), o que exercita de ponta a ponta o mesmo par escrita/leitura que um Shapefile
 * de verdade (QGIS etc.) produziria.
 */
class ImportacaoGeoServiceTest {

    private final ImportacaoGeoService service = new ImportacaoGeoService();
    private final GeometryFactory factory = new GeometryFactory();

    private Polygon quadrado(double lon0, double lat0, double lado) {
        Coordinate[] coordenadas = {
            new Coordinate(lon0, lat0),
            new Coordinate(lon0 + lado, lat0),
            new Coordinate(lon0 + lado, lat0 + lado),
            new Coordinate(lon0, lat0 + lado),
            new Coordinate(lon0, lat0),
        };
        return factory.createPolygon(coordenadas);
    }

    private byte[] criarShapefileZip(Polygon poligono, String codigoEpsg) throws Exception {
        Path diretorio = Files.createTempDirectory("shp-fixture-");
        File arquivoShp = diretorio.resolve("talhao.shp").toFile();

        ShapefileDataStoreFactory factoryLoja = new ShapefileDataStoreFactory();
        Map<String, Serializable> parametros = new HashMap<>();
        parametros.put("url", arquivoShp.toURI().toURL());
        ShapefileDataStore loja = (ShapefileDataStore) factoryLoja.createNewDataStore(parametros);

        SimpleFeatureTypeBuilder tipoBuilder = new SimpleFeatureTypeBuilder();
        tipoBuilder.setName("talhao");
        if (codigoEpsg != null) {
            CoordinateReferenceSystem crs = CRS.decode(codigoEpsg);
            tipoBuilder.setCRS(crs);
        }
        tipoBuilder.add("the_geom", Polygon.class);
        tipoBuilder.add("nome", String.class);
        SimpleFeatureType tipo = tipoBuilder.buildFeatureType();
        loja.createSchema(tipo);

        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(tipo);
        featureBuilder.add(poligono);
        featureBuilder.add("Talhão Teste");
        SimpleFeature feature = featureBuilder.buildFeature(null);
        DefaultFeatureCollection colecao = new DefaultFeatureCollection();
        colecao.add(feature);

        SimpleFeatureStore featureStore = (SimpleFeatureStore) loja.getFeatureSource();
        featureStore.addFeatures(colecao);
        loja.dispose();

        return zipar(diretorio);
    }

    private byte[] zipar(Path diretorio) throws Exception {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(saida)) {
            try (var arquivos = Files.list(diretorio)) {
                for (Path arquivo : arquivos.toList()) {
                    zip.putNextEntry(new ZipEntry(arquivo.getFileName().toString()));
                    zip.write(Files.readAllBytes(arquivo));
                    zip.closeEntry();
                }
            }
        }
        return saida.toByteArray();
    }

    @Test
    void importaShapefileZipValidoEProduzAGeometriaCorreta() throws Exception {
        Polygon original = quadrado(-48.50, -1.45, 0.01);
        byte[] zip = criarShapefileZip(original, "EPSG:4326");

        Geometry resultado = service.extrairGeometria("talhao.zip", zip);

        assertThat(resultado.getGeometryType()).isIn("Polygon", "MultiPolygon");
        assertThat(resultado.getEnvelopeInternal().getMinX()).isCloseTo(-48.50, within(1e-6));
        assertThat(resultado.getEnvelopeInternal().getMinY()).isCloseTo(-1.45, within(1e-6));
    }

    @Test
    void reprojetaShapefileComCrsDiferenteDe4326() throws Exception {
        // EPSG:3857 (Web Mercator) -- coordenadas em metros, nao em graus.
        double xMercator = -5400000.0;
        double yMercator = -160000.0;
        Polygon poligonoMercator = quadrado(xMercator, yMercator, 1000.0);
        byte[] zip = criarShapefileZip(poligonoMercator, "EPSG:3857");

        Geometry resultado = service.extrairGeometria("talhao.zip", zip);

        // Depois de reprojetado pra WGS84, as coordenadas devem estar na faixa de
        // graus (nao mais nos milhoes de metros do Mercator).
        assertThat(Math.abs(resultado.getEnvelopeInternal().getMinX())).isLessThan(180.0);
        assertThat(Math.abs(resultado.getEnvelopeInternal().getMinY())).isLessThan(90.0);
    }

    @Test
    void rejeitaShpIsoladoSemArquivosCompanheiros() {
        byte[] shpSozinho = new byte[] {0x00, 0x00, 0x27, 0x0a};

        assertThatThrownBy(() -> service.extrairGeometria("talhao.shp", shpSozinho))
                .isInstanceOf(GeometriaInvalidaException.class);
    }

    @Test
    void rejeitaZipSemNenhumShpDentro() throws Exception {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(saida)) {
            zip.putNextEntry(new ZipEntry("leiame.txt"));
            zip.write("nada de geometria aqui".getBytes());
            zip.closeEntry();
        }

        assertThatThrownBy(() -> service.extrairGeometria("talhao.zip", saida.toByteArray()))
                .isInstanceOf(GeometriaInvalidaException.class)
                .hasMessageContaining("Nenhum arquivo .shp");
    }

    @Test
    void rejeitaZipVazioOuCorrompido() {
        byte[] zipInvalido = new byte[] {0x01, 0x02, 0x03};

        assertThatThrownBy(() -> service.extrairGeometria("talhao.zip", zipInvalido))
                .isInstanceOf(GeometriaInvalidaException.class);
    }
}
