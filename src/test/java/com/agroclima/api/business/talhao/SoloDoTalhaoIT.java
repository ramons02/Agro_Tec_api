package com.agroclima.api.business.talhao;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.core.geo.GeometriaUtil;
import com.agroclima.api.core.security.JwtService;
import com.agroclima.api.support.ClimaIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/** GET /talhoes/{id}/solo: textura e CAD do SoilGrids gravados no talhao + umidade do modelo na hora atual. */
class SoloDoTalhaoIT extends ClimaIntegrationTestBase {

    private static final double LON = -48.50;
    private static final double LAT = -1.45;

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PropriedadeRepository propriedadeRepository;
    @Autowired
    private TalhaoRepository talhaoRepository;
    @Autowired
    private JwtService jwtService;

    private record Fixture(String token, Talhao talhao) {}

    private Fixture fixture(String email, boolean comSolo) {
        Usuario dono = usuarioRepository.save(new Usuario("Dono Solo", email, "hash-irrelevante", Papel.PRODUTOR_RURAL));
        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda Solo", "Belém", dono.getId(), null));
        var quadrado = GeometriaUtil.FACTORY_4326.createPolygon(new Coordinate[] {
                new Coordinate(LON, LAT), new Coordinate(LON + 0.01, LAT), new Coordinate(LON + 0.01, LAT + 0.01),
                new Coordinate(LON, LAT + 0.01), new Coordinate(LON, LAT)});
        Talhao talhao = new Talhao(propriedade.getId(), "Solo", GeometriaUtil.normalizarParaMultiPolygon(quadrado), 120.0);
        if (comSolo) {
            talhao.parametrizarSolo(TipoSolo.MISTO, 25.0, 40.0, 35.0, 2.0, 100.0);
        }
        return new Fixture(jwtService.criarToken(dono.getId(), Papel.PRODUTOR_RURAL).token(), talhaoRepository.save(talhao));
    }

    private void stubUmidade() {
        Instant hora = Instant.now().truncatedTo(ChronoUnit.HOURS);
        String corpo = "{\"hourly\":{\"time\":[\"" + hora.minus(1, ChronoUnit.HOURS).toString().replace("Z", "")
                + "\",\"" + hora.toString().replace("Z", "") + "\",\"" + hora.plus(1, ChronoUnit.HOURS).toString().replace("Z", "")
                + "\"],\"soil_moisture_0_to_7cm\":[0.10,0.231,0.40],\"soil_moisture_7_to_28cm\":[0.11,0.287,0.41]}}";
        OPENMETEO_MOCK.stubFor(get(urlPathEqualTo("/v1/forecast"))
                .withQueryParam("hourly", equalTo("soil_moisture_0_to_7cm,soil_moisture_7_to_28cm"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(corpo)));
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> chamar(Fixture f) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(f.token());
        return restTemplate.exchange("/api/v1/talhoes/" + f.talhao().getId() + "/solo", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void devolveTexturaCadEUmidadeDaHoraAtual() {
        stubUmidade();
        Map<String, Object> dados = (Map<String, Object>) chamar(fixture("solo-a@teste.com", true)).getBody().get("dados");

        assertThat(dados.get("tipo_solo")).isEqualTo("MISTO");
        assertThat(((Number) dados.get("capacidade_agua_disponivel_mm")).doubleValue()).isEqualTo(100.0);
        assertThat(dados.get("capacidade_campo_pct")).isNotNull();
        assertThat(dados.get("solo_pendente")).isEqualTo(false);
        assertThat(((Number) dados.get("umidade_0_7cm_pct")).doubleValue()).isEqualTo(23.1);
        assertThat(((Number) dados.get("umidade_7_28cm_pct")).doubleValue()).isEqualTo(28.7);
        assertThat(dados.get("fonte_umidade")).isEqualTo("OPEN_METEO");
    }

    @Test
    @SuppressWarnings("unchecked")
    void talhaoSemSoloVemPendenteEComNuloNuncaNumeroInventado() {
        stubUmidade();
        Map<String, Object> dados = (Map<String, Object>) chamar(fixture("solo-b@teste.com", false)).getBody().get("dados");

        assertThat(dados.get("solo_pendente")).isEqualTo(true);
        assertThat(dados.get("tipo_solo")).isNull();
        assertThat(dados.get("capacidade_agua_disponivel_mm")).isNull();
        assertThat(dados.get("capacidade_campo_pct")).isNull();
        assertThat(dados.get("umidade_0_7cm_pct")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void openMeteoForaDoArNaoDerrubaOSoloGravado() {
        stubOpenMeteoIndisponivel();
        ResponseEntity<Map> resposta = chamar(fixture("solo-c@teste.com", true));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = (Map<String, Object>) resposta.getBody().get("dados");
        assertThat(dados.get("tipo_solo")).isEqualTo("MISTO");
        assertThat(dados.get("umidade_0_7cm_pct")).isNull();
    }
}
