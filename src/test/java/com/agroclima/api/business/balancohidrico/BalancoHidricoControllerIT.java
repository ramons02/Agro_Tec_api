package com.agroclima.api.business.balancohidrico;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.talhao.Talhao;
import com.agroclima.api.business.talhao.TalhaoRepository;
import com.agroclima.api.business.talhao.TipoSolo;
import com.agroclima.api.core.geo.GeometriaUtil;
import com.agroclima.api.core.security.JwtService;
import com.agroclima.api.support.ClimaIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Balanco hidrico e recomendacao (RN007/RN023). Sem estacoes: a chuva e a ET0 do dia vem da analise do
 * Open-Meteo no ponto do talhao (campo daily com past_days), e a chuva prevista vem da previsao.
 */
class BalancoHidricoControllerIT extends ClimaIntegrationTestBase {

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
    private BalancoHidricoService balancoHidricoService;

    @Autowired
    private BalancoHidricoDiarioRepository balancoHidricoDiarioRepository;

    @Autowired
    private JwtService jwtService;

    private Polygon quadrado(double lon0, double lat0, double lado) {
        return GeometriaUtil.FACTORY_4326.createPolygon(new Coordinate[] {
                new Coordinate(lon0, lat0),
                new Coordinate(lon0 + lado, lat0),
                new Coordinate(lon0 + lado, lat0 + lado),
                new Coordinate(lon0, lat0 + lado),
                new Coordinate(lon0, lat0),
        });
    }

    /** Chuva e ET0 do dia ja ocorrido (chamada com past_days). Valor nulo simula "provedor sem dado". */
    private void stubDadosDiarios(LocalDate dia, Double chuvaMm, double et0) {
        String corpo = "{\"daily\":{\"time\":[\"" + dia + "\"],\"precipitation_sum\":[" + chuvaMm + "],"
                + "\"et0_fao_evapotranspiration\":[" + et0 + "]}}";
        OPENMETEO_MOCK.stubFor(get(urlPathEqualTo("/v1/forecast"))
                .withQueryParam("past_days", equalTo("3"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(corpo)));
    }

    /** Previsao (chamada com hourly): so interessa a chuva prevista, que entra no status do plantio. */
    private void stubPrevisao(double chuvaPrevistaMm) {
        String corpo = "{"
                + "\"hourly\":{\"wind_speed_10m\":[5.0],\"wind_speed_100m\":[8.0],"
                + "\"soil_moisture_0_to_7cm\":[0.3],\"soil_moisture_7_to_28cm\":[0.35]},"
                + "\"daily\":{\"et0_fao_evapotranspiration\":[3.0],\"precipitation_sum\":[" + chuvaPrevistaMm + "]}"
                + "}";
        OPENMETEO_MOCK.stubFor(get(urlPathEqualTo("/v1/forecast"))
                .withQueryParam("hourly", matching(".*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(corpo)));
    }

    private record Fixture(String token, Talhao talhao) {}

    private Fixture prepararFixture(String email, double areaHa) {
        Usuario usuario = usuarioRepository.save(new Usuario("Dono BH", email, "hash-irrelevante", Papel.PRODUTOR_RURAL));
        String token = jwtService.criarToken(usuario.getId(), usuario.getPapel()).token();

        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda BH", "Belém", usuario.getId(), null));
        Talhao talhao = new Talhao(
                propriedade.getId(), "Talhão BH", GeometriaUtil.normalizarParaMultiPolygon(quadrado(LON, LAT, 0.01)), areaHa);
        talhao.parametrizarSolo(TipoSolo.MISTO, 25.0, 40.0, 35.0, 2.0, 100.0);
        talhao = talhaoRepository.save(talhao);

        return new Fixture(token, talhao);
    }

    private HttpHeaders headersCom(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private LocalDate hoje() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    @Test
    void balancoHidricoSemCalculoPrevioRetorna404() {
        Fixture fixture = prepararFixture("dono-bh1@teste.com", 100.0);
        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/talhoes/" + fixture.talhao().getId() + "/balanco-hidrico", HttpMethod.GET,
                new HttpEntity<>(headersCom(fixture.token())), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void calculaBalancoHidricoEAmareloComArmazenamentoNaFaixaSemChuvaPrevista() {
        // CAD=100, sem historico -> armAnterior=70; chuva do dia 0; ET0=5, Kc fallback=0.4 -> etReal=2
        // armazenamento = 70+0-2 = 68 (68% da CAD) -- na faixa 60-90% mas sem 5mm previstos -> AMARELO
        Fixture fixture = prepararFixture("dono-bh2@teste.com", 100.0);
        stubDadosDiarios(hoje(), 0.0, 5.0);
        stubPrevisao(0.0);

        var resultado = balancoHidricoService.calcularBalancoHidricoDoTalhao(fixture.talhao(), hoje());
        assertThat(resultado).isPresent();
        assertThat(resultado.get().getArmazenamentoMm()).isCloseTo(68.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(resultado.get().getStatusPlantio().name()).isEqualTo("AMARELO");

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/talhoes/" + fixture.talhao().getId() + "/balanco-hidrico", HttpMethod.GET,
                new HttpEntity<>(headersCom(fixture.token())), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = (Map<String, Object>) resposta.getBody().get("dados");
        assertThat(((Number) dados.get("percentual_cad")).doubleValue()).isCloseTo(68.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void chuvaOcorridaNoDiaEntraNoArmazenamento() {
        // 70 + 10 de chuva - 2 de ET real (5 * 0.4) = 78
        Fixture fixture = prepararFixture("dono-bh5@teste.com", 100.0);
        stubDadosDiarios(hoje(), 10.0, 5.0);
        stubPrevisao(0.0);

        var resultado = balancoHidricoService.calcularBalancoHidricoDoTalhao(fixture.talhao(), hoje());

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getArmazenamentoMm()).isCloseTo(78.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void semValorDeChuvaDoDiaNaoCalculaENaoAssumeZeroMilimetros() {
        Fixture fixture = prepararFixture("dono-bh6@teste.com", 100.0);
        stubDadosDiarios(hoje(), null, 5.0);
        stubPrevisao(0.0);

        var resultado = balancoHidricoService.calcularBalancoHidricoDoTalhao(fixture.talhao(), hoje());

        assertThat(resultado).isEmpty();
        assertThat(balancoHidricoDiarioRepository.findFirstByTalhaoIdOrderByDataDesc(fixture.talhao().getId())).isEmpty();
    }

    @Test
    void comOpenMeteoForaDoArNaoCalcula() {
        Fixture fixture = prepararFixture("dono-bh7@teste.com", 100.0);
        stubOpenMeteoIndisponivel();

        assertThat(balancoHidricoService.calcularBalancoHidricoDoTalhao(fixture.talhao(), hoje())).isEmpty();
    }

    @Test
    void jobDiarioCalculaOBalancoDoDiaAnterior() {
        Fixture fixture = prepararFixture("dono-bh8@teste.com", 100.0);
        LocalDate ontem = hoje().minusDays(1);
        stubDadosDiarios(ontem, 0.0, 5.0);
        stubPrevisao(0.0);

        var resumo = balancoHidricoService.calcularBalancoHidricoTodosTalhoes();

        assertThat(resumo.talhoesCalculados()).isEqualTo(1);
        var gravado = balancoHidricoDiarioRepository.findFirstByTalhaoIdOrderByDataDesc(fixture.talhao().getId());
        assertThat(gravado).isPresent();
        assertThat(gravado.get().getData()).isEqualTo(ontem);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recomendacaoPrioridadeAltaQuandoStatusVermelho() {
        // ET0 bem alto -> etReal=110*0.4=44 -> armazenamento=70-44=26 (26% < 30%) -> VERMELHO -> prioridade ALTA
        Fixture fixture = prepararFixture("dono-bh3@teste.com", 100.0);
        stubDadosDiarios(hoje(), 0.0, 110.0);
        stubPrevisao(0.0);
        balancoHidricoService.calcularBalancoHidricoDoTalhao(fixture.talhao(), hoje());

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/talhoes/" + fixture.talhao().getId() + "/recomendacao", HttpMethod.GET,
                new HttpEntity<>(headersCom(fixture.token())), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = (Map<String, Object>) resposta.getBody().get("dados");
        assertThat(dados.get("prioridade")).isEqualTo("ALTA");
        assertThat((String) dados.get("texto")).contains("risco crítico");
    }

    @Test
    @SuppressWarnings("unchecked")
    void recomendacaoSemBalancoAindaMostraTextoDeAusencia() {
        Fixture fixture = prepararFixture("dono-bh4@teste.com", 100.0);
        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/talhoes/" + fixture.talhao().getId() + "/recomendacao", HttpMethod.GET,
                new HttpEntity<>(headersCom(fixture.token())), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = (Map<String, Object>) resposta.getBody().get("dados");
        assertThat((String) dados.get("texto")).contains("Ainda sem balanço hídrico calculado");
    }
}
