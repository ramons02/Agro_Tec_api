package com.agroclima.api.business.talhao;

import com.agroclima.api.core.geo.GeoJsonUtil;
import com.agroclima.api.core.geo.GeometriaInvalidaException;
import com.agroclima.api.core.geo.GeometriaUtil;
import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Espelha app/services/importacao_geo_service.py -- extrai geometria de GeoJSON/KML/Shapefile.
 * Shapefile via GeoTools (spec 019, User Story 2) -- unico formato que exige biblioteca externa
 * (arquivo binario multi-parte), GeoJSON/KML cobrem o caminho comum sem dependencia pesada.
 */
@Service
public class ImportacaoGeoService {

    private static final String NAMESPACE_KML = "http://www.opengis.net/kml/2.2";

    public Geometry extrairGeometria(String nomeArquivo, byte[] conteudo) {
        String extensao = extensao(nomeArquivo);
        return switch (extensao) {
            case "geojson", "json" -> GeoJsonUtil.parseGeometria(new String(conteudo, StandardCharsets.UTF_8));
            case "kml" -> extrairDeKml(conteudo);
            case "shp" -> throw new GeometriaInvalidaException(
                    "Um arquivo .shp sozinho não é suficiente -- envie um .zip com o conjunto completo "
                            + "(.shp, .dbf, .shx e, se possível, .prj).");
            case "zip" -> extrairDeShapefile(conteudo);
            default -> throw new GeometriaInvalidaException("Formato de arquivo não suportado: ." + extensao);
        };
    }

    private String extensao(String nomeArquivo) {
        int ponto = nomeArquivo.lastIndexOf('.');
        if (ponto < 0 || ponto == nomeArquivo.length() - 1) {
            throw new GeometriaInvalidaException("Arquivo sem extensão reconhecível.");
        }
        return nomeArquivo.substring(ponto + 1).toLowerCase(Locale.ROOT);
    }

    private Polygon extrairDeKml(byte[] conteudo) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document documento = builder.parse(new ByteArrayInputStream(conteudo));
            NodeList coordenadasNodes = documento.getElementsByTagNameNS(NAMESPACE_KML, "coordinates");
            for (int i = 0; i < coordenadasNodes.getLength(); i++) {
                Element elemento = (Element) coordenadasNodes.item(i);
                List<Coordinate> coordenadas = parseCoordenadasKml(elemento.getTextContent());
                if (coordenadas.size() >= 4) {
                    return construirPoligono(coordenadas);
                }
            }
            throw new GeometriaInvalidaException("Nenhum polígono válido (>=4 pontos) encontrado no KML.");
        } catch (GeometriaInvalidaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GeometriaInvalidaException("KML inválido ou corrompido.");
        }
    }

    private List<Coordinate> parseCoordenadasKml(String texto) {
        List<Coordinate> coordenadas = new ArrayList<>();
        for (String tupla : texto.trim().split("\\s+")) {
            if (tupla.isBlank()) {
                continue;
            }
            String[] partes = tupla.split(",");
            double lon = Double.parseDouble(partes[0]);
            double lat = Double.parseDouble(partes[1]);
            coordenadas.add(new Coordinate(lon, lat));
        }
        return coordenadas;
    }

    private Polygon construirPoligono(List<Coordinate> coordenadas) {
        List<Coordinate> anel = new ArrayList<>(coordenadas);
        if (!anel.get(0).equals2D(anel.get(anel.size() - 1))) {
            anel.add(new Coordinate(anel.get(0)));
        }
        LinearRing linearRing = GeometriaUtil.FACTORY_4326.createLinearRing(anel.toArray(new Coordinate[0]));
        return GeometriaUtil.FACTORY_4326.createPolygon(linearRing);
    }

    private Geometry extrairDeShapefile(byte[] conteudoZip) {
        Path diretorioTemporario;
        try {
            diretorioTemporario = Files.createTempDirectory("shapefile-import-");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        try {
            File arquivoShp = extrairZipEEncontrarShp(conteudoZip, diretorioTemporario);
            return lerGeometriaDoShapefile(arquivoShp);
        } finally {
            limparDiretorio(diretorioTemporario);
        }
    }

    private File extrairZipEEncontrarShp(byte[] conteudoZip, Path diretorioDestino) {
        File shp = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(conteudoZip))) {
            ZipEntry entrada;
            boolean algumaEntrada = false;
            while ((entrada = zip.getNextEntry()) != null) {
                algumaEntrada = true;
                if (entrada.isDirectory()) {
                    continue;
                }
                String nomeEntrada = Path.of(entrada.getName()).getFileName().toString();
                Path destino = diretorioDestino.resolve(nomeEntrada);
                Files.copy(zip, destino);
                if (nomeEntrada.toLowerCase(Locale.ROOT).endsWith(".shp")) {
                    shp = destino.toFile();
                }
            }
            if (!algumaEntrada) {
                throw new GeometriaInvalidaException("Arquivo .zip vazio ou corrompido.");
            }
        } catch (IOException ex) {
            throw new GeometriaInvalidaException("Arquivo .zip inválido ou corrompido.");
        }
        if (shp == null) {
            throw new GeometriaInvalidaException("Nenhum arquivo .shp encontrado dentro do .zip.");
        }
        return shp;
    }

    private Geometry lerGeometriaDoShapefile(File arquivoShp) {
        FileDataStore dataStore;
        try {
            dataStore = FileDataStoreFinder.getDataStore(arquivoShp);
        } catch (IOException ex) {
            throw new GeometriaInvalidaException(
                    "Shapefile inválido ou incompleto (verifique se .dbf e .shx estão no .zip).");
        }
        if (dataStore == null) {
            throw new GeometriaInvalidaException("Shapefile inválido ou incompleto.");
        }
        try {
            SimpleFeatureSource fonte = dataStore.getFeatureSource();
            CoordinateReferenceSystem crsOrigem = fonte.getSchema().getCoordinateReferenceSystem();

            SimpleFeature primeiraFeature;
            try (var iterador = fonte.getFeatures().features()) {
                if (!iterador.hasNext()) {
                    throw new GeometriaInvalidaException("Shapefile sem nenhuma feature de geometria.");
                }
                primeiraFeature = iterador.next();
            }

            Geometry geometria = (Geometry) primeiraFeature.getDefaultGeometry();
            if (geometria == null) {
                throw new GeometriaInvalidaException("Shapefile sem geometria válida na primeira feature.");
            }
            geometria = reprojetarSeNecessario(geometria, crsOrigem);
            return GeometriaUtil.FACTORY_4326.createGeometry(geometria);
        } catch (IOException ex) {
            throw new GeometriaInvalidaException("Falha ao ler as features do Shapefile.");
        } finally {
            dataStore.dispose();
        }
    }

    private Geometry reprojetarSeNecessario(Geometry geometria, CoordinateReferenceSystem crsOrigem) {
        if (crsOrigem == null) {
            // Sem .prj no Shapefile -- assume que ja esta em WGS84 (EPSG:4326), mesma
            // convencao adotada pelo GeoJSON/KML (nenhum dos dois carrega CRS proprio).
            return geometria;
        }
        try {
            CoordinateReferenceSystem crsDestino = CRS.decode("EPSG:4326");
            if (CRS.equalsIgnoreMetadata(crsOrigem, crsDestino)) {
                return geometria;
            }
            MathTransform transformacao = CRS.findMathTransform(crsOrigem, crsDestino, true);
            return JTS.transform(geometria, transformacao);
        } catch (FactoryException | TransformException ex) {
            throw new GeometriaInvalidaException("Não foi possível reprojetar o sistema de referência do Shapefile.");
        }
    }

    private void limparDiretorio(Path diretorio) {
        try (Stream<Path> arquivos = Files.walk(diretorio)) {
            arquivos.sorted(Comparator.reverseOrder()).forEach(caminho -> {
                try {
                    Files.delete(caminho);
                } catch (IOException ignorada) {
                    // best-effort: diretorio temporario, sem impacto funcional se sobrar lixo
                }
            });
        } catch (IOException ignorada) {
            // best-effort
        }
    }
}
