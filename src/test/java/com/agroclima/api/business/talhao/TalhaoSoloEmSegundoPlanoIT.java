package com.agroclima.api.business.talhao;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.balancohidrico.BalancoHidricoDiarioRepository;
import com.agroclima.api.business.clima.clients.FonteSoloIndisponivelException;
import com.agroclima.api.business.clima.clients.PerfilSolo;
import com.agroclima.api.business.clima.clients.SoilGridsClient;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * O SoilGrids leva de 30 s a mais de 1 min, entao o solo do talhao e buscado fora do cadastro: o POST responde
 * na hora e TalhaoSoloService completa textura, CAD e o balanco do dia depois (RN017: nunca derruba o talhao).
 */
@TestPropertySource(properties = "agroclima.solo.pausa-entre-tentativas-ms=1")
class TalhaoSoloEmSegundoPlanoIT extends ClimaIntegrationTestBase {

    private static final double LON = -48.50;
    private static final double LAT = -1.45;
    private static final PerfilSolo PERFIL = new PerfilSolo(25.0, 40.0, 35.0, 2.0, 1.3);

    @MockitoBean
    private SoilGridsClient soilGridsClient;

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PropriedadeRepository propriedadeRepository;
    @Autowired
    private TalhaoRepository talhaoRepository;
    @Autowired
    private BalancoHidricoDiarioRepository balancoRepository;
    @Autowired
    private TalhaoSoloService talhaoSoloService;
    @Autowired
    private JwtService jwtService;

    private Propriedade propriedadeNova(String email) {
        Usuario dono = usuarioRepository.save(new Usuario("Dono Solo", email, "hash-irrelevante", Papel.PRODUTOR_RURAL));
        return propriedadeRepository.save(new Propriedade("Fazenda Solo", "Belém", dono.getId(), null));
    }

    private Talhao talhaoSemSolo(Propriedade propriedade) {
        var quadrado = GeometriaUtil.FACTORY_4326.createPolygon(new Coordinate[] {
                new Coordinate(LON, LAT), new Coordinate(LON + 0.01, LAT), new Coordinate(LON + 0.01, LAT + 0.01),
                new Coordinate(LON, LAT + 0.01), new Coordinate(LON, LAT)});
        return talhaoRepository.save(
                new Talhao(propriedade.getId(), "Sem solo", GeometriaUtil.normalizarParaMultiPolygon(quadrado), 120.0));
    }

    private void stubChuvaDeOntem() {
        LocalDate ontem = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        String corpo = "{\"daily\":{\"time\":[\"" + ontem + "\"],\"precipitation_sum\":[0.0],"
                + "\"et0_fao_evapotranspiration\":[5.0]}}";
        OPENMETEO_MOCK.stubFor(get(urlPathEqualTo("/v1/forecast"))
                .withQueryParam("past_days", equalTo("3"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(corpo)));
    }

    @Test
    void completaTexturaCadEBalancoDoDia() {
        Talhao talhao = talhaoSemSolo(propriedadeNova("solo1@teste.com"));
        when(soilGridsClient.parametrizarSolo(anyDouble(), anyDouble())).thenReturn(Optional.of(PERFIL));
        stubChuvaDeOntem();

        assertThat(talhaoSoloService.completar(talhao.getId())).isTrue();

        Talhao atualizado = talhaoRepository.findById(talhao.getId()).orElseThrow();
        assertThat(atualizado.getTipoSolo()).isEqualTo(TipoSolo.MISTO);
        assertThat(atualizado.getCapacidadeAguaDisponivelMm()).isNotNull();
        assertThat(balancoRepository.findFirstByTalhaoIdOrderByDataDesc(talhao.getId())).isPresent();
    }

    @Test
    void tentaDeNovoQuandoOSoilGridsFalhaEGravaNaTerceira() {
        Talhao talhao = talhaoSemSolo(propriedadeNova("solo2@teste.com"));
        when(soilGridsClient.parametrizarSolo(anyDouble(), anyDouble()))
                .thenThrow(new FonteSoloIndisponivelException("fora", null))
                .thenThrow(new FonteSoloIndisponivelException("fora", null))
                .thenReturn(Optional.of(PERFIL));
        stubChuvaDeOntem();

        assertThat(talhaoSoloService.completar(talhao.getId())).isTrue();

        verify(soilGridsClient, times(3)).parametrizarSolo(anyDouble(), anyDouble());
        assertThat(talhaoRepository.findById(talhao.getId()).orElseThrow().getTipoSolo()).isNotNull();
    }

    @Test
    void soilGridsForaDoArNaoDerrubaNemApagaOTalhaoEOJobTentaDeNovo() {
        Talhao talhao = talhaoSemSolo(propriedadeNova("solo3@teste.com"));
        when(soilGridsClient.parametrizarSolo(anyDouble(), anyDouble()))
                .thenThrow(new FonteSoloIndisponivelException("fora", null));

        assertThat(talhaoSoloService.completar(talhao.getId())).isFalse();
        assertThat(talhaoRepository.findById(talhao.getId()).orElseThrow().getTipoSolo()).isNull();

        doReturn(Optional.of(PERFIL)).when(soilGridsClient).parametrizarSolo(anyDouble(), anyDouble());
        stubChuvaDeOntem();
        assertThat(talhaoSoloService.completarPendentes()).isEqualTo(1);
        assertThat(talhaoRepository.findById(talhao.getId()).orElseThrow().getTipoSolo()).isNotNull();
    }

    @Test
    void localSemCoberturaNaoEPerguntadoDeNovoARodada() {
        Talhao talhao = talhaoSemSolo(propriedadeNova("solo4@teste.com"));
        when(soilGridsClient.parametrizarSolo(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        assertThat(talhaoSoloService.completarPendentes()).isZero();
        assertThat(talhaoSoloService.completarPendentes()).isZero();

        verify(soilGridsClient, times(1)).parametrizarSolo(anyDouble(), anyDouble());
        assertThat(talhaoRepository.findById(talhao.getId()).orElseThrow().getTipoSolo()).isNull();
    }

    @Test
    void cadastroResponde200SemEsperarOSoilGrids() {
        Propriedade propriedade = propriedadeNova("solo5@teste.com");
        String token = jwtService.criarToken(propriedade.getProprietarioId(), Papel.PRODUTOR_RURAL).token();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String corpo = "{\"propriedade_id\":\"" + propriedade.getId() + "\",\"nome\":\"Novo\",\"geometria\":"
                + "{\"type\":\"Polygon\",\"coordinates\":[[[-48.50,-1.45],[-48.49,-1.45],[-48.49,-1.44],"
                + "[-48.50,-1.44],[-48.50,-1.45]]]}}";

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/talhoes", HttpMethod.POST, new HttpEntity<>(corpo, headers), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        verifyNoInteractions(soilGridsClient);
    }
}
