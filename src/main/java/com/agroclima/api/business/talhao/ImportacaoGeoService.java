package com.agroclima.api.business.talhao;

import com.agroclima.api.core.geo.GeoJsonUtil;
import com.agroclima.api.core.geo.GeometriaInvalidaException;
import com.agroclima.api.core.geo.GeometriaUtil;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Espelha app/services/importacao_geo_service.py -- extrai geometria de GeoJSON/KML/Shapefile.
 * Shapefile ainda NAO implementado nesta versao (gap disclosed) -- exige GeoTools, dependencia
 * pesada nao adicionada ainda; GeoJSON e KML cobrem o caminho comum sem dependencia nova.
 */
@Service
public class ImportacaoGeoService {

    private static final String NAMESPACE_KML = "http://www.opengis.net/kml/2.2";

    public Geometry extrairGeometria(String nomeArquivo, byte[] conteudo) {
        String extensao = extensao(nomeArquivo);
        return switch (extensao) {
            case "geojson", "json" -> GeoJsonUtil.parseGeometria(new String(conteudo, StandardCharsets.UTF_8));
            case "kml" -> extrairDeKml(conteudo);
            case "shp", "zip" -> throw new GeometriaInvalidaException(
                    "Importação de Shapefile ainda não implementada nesta versão do backend.");
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
}
