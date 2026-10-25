package com.agroclima.api.support;

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

/** Gera um .zip de Shapefile de verdade (via GeoTools) para os testes: nao ha .shp de exemplo no repositorio. */
public final class ShapefileFixture {

    private ShapefileFixture() {}

    public static byte[] zip(Polygon poligono, String codigoEpsg) throws Exception {
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

    private static byte[] zipar(Path diretorio) throws Exception {
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
}
