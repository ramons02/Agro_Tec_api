package com.agroclima.api.business.telegram;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.estacao.EstacaoInmet;
import com.agroclima.api.business.estacao.EstacaoInmetRepository;
import com.agroclima.api.business.estacao.FonteDados;
import com.agroclima.api.business.estacao.MedicaoClima;
import com.agroclima.api.business.estacao.MedicaoClimaRepository;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.talhao.Talhao;
import com.agroclima.api.business.talhao.TalhaoRepository;
import com.agroclima.api.core.calculos.ClassificacaoPulverizacao;
import com.agroclima.api.core.geo.GeometriaUtil;
import com.agroclima.api.support.TelegramIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/** Espelha spec.md (feature 017, US2/US3) -- estados montados diretamente no banco (nao via
 * ingestao real), fresco o bastante (Instant.now()) pra climarTempoRealService nunca precisar
 * chamar INMET/Open-Meteo de verdade. */
class AlertaPulverizacaoServiceIT extends TelegramIntegrationTestBase {

    private static final double LON = -47.90;
    private static final double LAT = -1.40;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PropriedadeRepository propriedadeRepository;

    @Autowired
    private TalhaoRepository talhaoRepository;

    @Autowired
    private EstacaoInmetRepository estacaoInmetRepository;

    @Autowired
    private MedicaoClimaRepository medicaoClimaRepository;

    @Autowired
    private EstadoAlertaPulverizacaoRepository estadoAlertaPulverizacaoRepository;

    @Autowired
    private AlertaPulverizacaoService alertaPulverizacaoService;

    private Polygon quadrado(double lon0, double lat0, double lado) {
        return GeometriaUtil.FACTORY_4326.createPolygon(new Coordinate[] {
                new Coordinate(lon0, lat0),
                new Coordinate(lon0 + lado, lat0),
                new Coordinate(lon0 + lado, lat0 + lado),
                new Coordinate(lon0, lat0 + lado),
                new Coordinate(lon0, lat0),
        });
    }

    private void stubTelegramOk() {
        TELEGRAM_MOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathMatching("/bot.*/sendMessage"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"ok\":true}")));
    }

    private record Fixture(Talhao talhao) {}

    /** ventoMs/rajadaMs em m/s (mesma unidade de MedicaoClima); telegramChatId nulo = dono sem
     * Telegram vinculado (cenario de "nunca gera chamada"). */
    private Fixture prepararFixture(String email, String telegramChatId, double deslocamento, double ventoMs,
            double rajadaMs, double temperaturaC, double umidadePct) {
        double lon = LON + deslocamento;
        double lat = LAT + deslocamento;

        Usuario dono = usuarioRepository.save(new Usuario("Dono Alerta", email, "hash-irrelevante", Papel.PRODUTOR_RURAL));
        if (telegramChatId != null) {
            dono.vincularTelegram(telegramChatId);
            dono = usuarioRepository.save(dono);
        }

        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda Alerta", "Belém", dono.getId(), null));
        Talhao talhao = new Talhao(
                propriedade.getId(), "Talhão Alerta",
                GeometriaUtil.normalizarParaMultiPolygon(quadrado(lon, lat, 0.01)), 50.0);
        talhao = talhaoRepository.save(talhao);

        var posicao = GeometriaUtil.FACTORY_4326.createPoint(new Coordinate(lon + 0.02, lat + 0.02));
        EstacaoInmet estacao = estacaoInmetRepository.save(
                new EstacaoInmet("ALT" + (int) (deslocamento * 1000), "Estação Alerta", "PA", posicao));
        medicaoClimaRepository.save(new MedicaoClima(
                estacao.getCodigo(), Instant.now(), 0.0, temperaturaC, umidadePct, ventoMs, rajadaMs, FonteDados.AO_VIVO));

        return new Fixture(talhao);
    }

    @Test
    void transicaoDeBloqueadaParaFavoravelEnviaAlertaDeJanela() {
        stubTelegramOk();
        // vento=1.0m/s(3.6km/h, dentro de 3-10), rajada=2.0m/s(7.2km/h, <15),
        // T=28/UR=60 -> deltaT~5.8 (dentro de 2-10) => FAVORAVEL.
        Fixture fixture = prepararFixture("dono-alerta1@teste.com", "111111", 0.00, 1.0, 2.0, 28.0, 60.0);
        estadoAlertaPulverizacaoRepository.save(new EstadoAlertaPulverizacao(
                fixture.talhao().getId(), ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE, Instant.now()));

        AlertaPulverizacaoService.ResumoAlertas resumo = alertaPulverizacaoService.processarTodosTalhoes();

        assertThat(resumo.alertasEnviados()).isGreaterThanOrEqualTo(1);
        TELEGRAM_MOCK.verify(postRequestedFor(urlPathMatching("/bot.*/sendMessage")));
        assertThat(estadoAlertaPulverizacaoRepository.findById(fixture.talhao().getId()).orElseThrow()
                .getUltimaClassificacao()).isEqualTo(ClassificacaoPulverizacao.FAVORAVEL);
    }

    @Test
    void permanecerFavoravelNaoRepeteAlertaDeJanela() {
        stubTelegramOk();
        Fixture fixture = prepararFixture("dono-alerta2@teste.com", "222222", 0.10, 1.0, 2.0, 28.0, 60.0);
        estadoAlertaPulverizacaoRepository.save(new EstadoAlertaPulverizacao(
                fixture.talhao().getId(), ClassificacaoPulverizacao.FAVORAVEL, Instant.now()));

        alertaPulverizacaoService.processarTodosTalhoes();

        TELEGRAM_MOCK.verify(exactly(0), postRequestedFor(urlPathMatching("/bot.*/sendMessage")));
    }

    @Test
    void rajadaExtremaEnviaAlertaMesmoSemTransicaoParaFavoravel() {
        stubTelegramOk();
        // rajada=6.0m/s(21.6km/h, >15) -- bloqueia por vento forte, mas dispara o alerta de
        // rajada extrema (US3) independente da classificacao final nao ser FAVORAVEL.
        Fixture fixture = prepararFixture("dono-alerta3@teste.com", "333333", 0.20, 1.0, 6.0, 28.0, 60.0);

        alertaPulverizacaoService.processarTodosTalhoes();

        TELEGRAM_MOCK.verify(postRequestedFor(urlPathMatching("/bot.*/sendMessage")));
        assertThat(estadoAlertaPulverizacaoRepository.findById(fixture.talhao().getId()).orElseThrow()
                .getUltimaClassificacao()).isEqualTo(ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE);
    }

    @Test
    void usuarioSemTelegramVinculadoNuncaGeraChamadaHttp() {
        stubTelegramOk();
        prepararFixture("dono-alerta4@teste.com", null, 0.30, 1.0, 2.0, 28.0, 60.0);

        alertaPulverizacaoService.processarTodosTalhoes();

        TELEGRAM_MOCK.verify(exactly(0), postRequestedFor(urlPathMatching("/bot.*/sendMessage")));
    }
}
