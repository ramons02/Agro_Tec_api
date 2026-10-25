package com.agroclima.api.business.talhao;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.core.geo.GeometriaUtil;
import com.agroclima.api.core.security.JwtService;
import com.agroclima.api.support.IntegrationTestBase;
import com.agroclima.api.support.ShapefileFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** POST /talhoes/geometria-de-arquivo: le GeoJSON, KML ou Shapefile (.zip) para a tela desenhar o poligono, sem cadastrar nada. */
class GeometriaDeArquivoIT extends IntegrationTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private JwtService jwtService;
    @Autowired private TalhaoRepository talhaoRepository;

    private String token;

    @BeforeEach
    void preparar() {
        Usuario u = usuarioRepository.save(new Usuario("Quem Importa", "importa@teste.com", "h", Papel.PRODUTOR_RURAL));
        token = jwtService.criarToken(u.getId(), u.getPapel()).token();
    }

    private static Polygon quadrado(double lon0, double lat0, double lado) {
        return GeometriaUtil.FACTORY_4326.createPolygon(new Coordinate[] {
            new Coordinate(lon0, lat0), new Coordinate(lon0 + lado, lat0),
            new Coordinate(lon0 + lado, lat0 + lado), new Coordinate(lon0, lat0 + lado), new Coordinate(lon0, lat0)});
    }

    private ResponseEntity<String> enviar(String nome, byte[] conteudo, String tokenUsado) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (tokenUsado != null) {
            headers.setBearerAuth(tokenUsado);
        }
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("arquivo", new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nome;
            }
        });
        return restTemplate.exchange("/api/v1/talhoes/geometria-de-arquivo", HttpMethod.POST,
                new HttpEntity<>(corpo, headers), String.class);
    }

    private static String geojson(double lon0, double lat0, double lado) {
        return "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[["
                + lon0 + "," + lat0 + "],[" + (lon0 + lado) + "," + lat0 + "],[" + (lon0 + lado) + "," + (lat0 + lado)
                + "],[" + lon0 + "," + (lat0 + lado) + "],[" + lon0 + "," + lat0 + "]]]}}";
    }

    @Test
    void shapefileDentroDoParaDevolveOPoligonoEDentroDoParaTrue() throws Exception {
        byte[] zip = ShapefileFixture.zip(quadrado(-48.50, -1.45, 0.01), "EPSG:4326");

        ResponseEntity<String> resposta = enviar("talhao.zip", zip, token);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode dados = JSON.readTree(resposta.getBody()).path("dados");
        assertThat(dados.path("dentro_do_para").asBoolean()).isTrue();
        assertThat(dados.path("mensagem").isNull()).isTrue();
        assertThat(dados.path("geometria").path("type").asText()).isIn("Polygon", "MultiPolygon");
        assertThat(dados.path("geometria").path("coordinates")).isNotEmpty();
    }

    @Test
    void shapefileForaDoParaVoltaComAGeometriaParaAPessoaVerOndeCaiEMensagem() throws Exception {
        // Imperatriz (MA)
        byte[] zip = ShapefileFixture.zip(quadrado(-47.48, -5.52, 0.01), "EPSG:4326");

        ResponseEntity<String> resposta = enviar("talhao.zip", zip, token);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode dados = JSON.readTree(resposta.getBody()).path("dados");
        assertThat(dados.path("dentro_do_para").asBoolean()).isFalse();
        assertThat(dados.path("mensagem").asText()).contains("dentro do estado do Pará");
        assertThat(dados.path("geometria").path("coordinates")).isNotEmpty();
    }

    @Test
    void shapefileEmOutroSistemaDeCoordenadasEConvertidoEFicaNaPosicaoCerta() throws Exception {
        // Belem em Web Mercator (EPSG:3857): ~(-5399000, -161400).
        Polygon mercator = GeometriaUtil.FACTORY_4326.createPolygon(new Coordinate[] {
            new Coordinate(-5_394_000, -161_500), new Coordinate(-5_393_000, -161_500),
            new Coordinate(-5_393_000, -160_500), new Coordinate(-5_394_000, -160_500), new Coordinate(-5_394_000, -161_500)});
        byte[] zip = ShapefileFixture.zip(mercator, "EPSG:3857");

        JsonNode dados = JSON.readTree(enviar("talhao.zip", zip, token).getBody()).path("dados");

        assertThat(dados.path("dentro_do_para").asBoolean()).isTrue();
        JsonNode ponto = dados.path("geometria").path("coordinates");
        while (ponto.isArray() && ponto.get(0).isArray()) {
            ponto = ponto.get(0);
        }
        double lon = ponto.get(0).asDouble();
        assertThat(lon).isBetween(-48.6, -48.4);
    }

    @Test
    void geojsonDentroEForaDoParaTambemFuncionam() throws Exception {
        JsonNode dentro = JSON.readTree(enviar("t.geojson", geojson(-49.13, -5.38, 0.01).getBytes(StandardCharsets.UTF_8), token).getBody()).path("dados");
        JsonNode fora = JSON.readTree(enviar("t.geojson", geojson(-46.61, -23.51, 0.01).getBytes(StandardCharsets.UTF_8), token).getBody()).path("dados");

        assertThat(dentro.path("dentro_do_para").asBoolean()).isTrue();
        assertThat(fora.path("dentro_do_para").asBoolean()).isFalse();
    }

    @Test
    void kmlDentroDoParaFunciona() throws Exception {
        String kml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><Placemark>"
                + "<Polygon><outerBoundaryIs><LinearRing><coordinates>-54.70,-2.44,0 -54.69,-2.44,0 -54.69,-2.43,0 -54.70,-2.43,0 -54.70,-2.44,0"
                + "</coordinates></LinearRing></outerBoundaryIs></Polygon></Placemark></Document></kml>";

        JsonNode dados = JSON.readTree(enviar("t.kml", kml.getBytes(StandardCharsets.UTF_8), token).getBody()).path("dados");

        assertThat(dados.path("dentro_do_para").asBoolean()).isTrue();
    }

    @Test
    void shpSozinhoPedeOZipCompleto() {
        ResponseEntity<String> resposta = enviar("talhao.shp", new byte[] {0, 0, 0x27, 0x0a}, token);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).contains(".zip");
    }

    @Test
    void zipSemOsArquivosDoShapefileEhRecusadoComMensagem() {
        ResponseEntity<String> resposta = enviar("talhao.zip", "isso nao e um zip".getBytes(StandardCharsets.UTF_8), token);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).contains("zip");
        assertThat(resposta.getBody()).doesNotContain("Exception").doesNotContain("Erro interno");
    }

    @Test
    void lerOArquivoNaoCadastraNada() throws Exception {
        enviar("talhao.zip", ShapefileFixture.zip(quadrado(-48.50, -1.45, 0.01), "EPSG:4326"), token);

        assertThat(talhaoRepository.count()).isZero();
    }

    @Test
    void semTokenResponde401() {
        ResponseEntity<String> resposta = enviar("t.geojson", geojson(-48.5, -1.45, 0.01).getBytes(StandardCharsets.UTF_8), null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
