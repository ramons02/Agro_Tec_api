package com.agroclima.api.business.telegram;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.talhao.Talhao;
import com.agroclima.api.business.talhao.TalhaoRepository;
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

/** US4 (spec 022, FR-017): conta suspensa nao recebe alerta do Telegram; conta ativa continua recebendo. */
class AlertaContaSuspensaIT extends TelegramIntegrationTestBase {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PropriedadeRepository propriedadeRepository;

    @Autowired
    private TalhaoRepository talhaoRepository;

    @Autowired
    private AlertaPulverizacaoService alertaPulverizacaoService;

    private Polygon quadrado(double lon0, double lat0, double lado) {
        return GeometriaUtil.FACTORY_4326.createPolygon(new Coordinate[] {
                new Coordinate(lon0, lat0), new Coordinate(lon0 + lado, lat0),
                new Coordinate(lon0 + lado, lat0 + lado), new Coordinate(lon0, lat0 + lado),
                new Coordinate(lon0, lat0),
        });
    }

    /** Rajada extrema (6 m/s = 21,6 km/h): dispara alerta mesmo sem transicao anterior. */
    private void prepararTalhaoComRajadaExtrema(Usuario dono) {
        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda", "Belém", dono.getId(), null));
        talhaoRepository.save(new Talhao(propriedade.getId(), "Talhão",
                GeometriaUtil.normalizarParaMultiPolygon(quadrado(-47.90, -1.40, 0.01)), 50.0));
        stubOpenMeteoCondicaoAtual(28.0, 60.0, 1.0 * 3.6, 6.0 * 3.6, 0.0);
        TELEGRAM_MOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathMatching("/bot.*/sendMessage"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"ok\":true}")));
    }

    private Usuario donoComTelegram(String email) {
        Usuario dono = new Usuario("Dono", email, "hash-irrelevante", Papel.PRODUTOR_RURAL);
        dono.vincularTelegram("999111");
        return usuarioRepository.save(dono);
    }

    @Test
    void contaSuspensaNaoRecebeAlerta() {
        Usuario dono = donoComTelegram("suspenso@teste.com");
        dono.suspender("atraso", Instant.now());
        usuarioRepository.save(dono);
        prepararTalhaoComRajadaExtrema(dono);

        alertaPulverizacaoService.processarTodosTalhoes();

        TELEGRAM_MOCK.verify(exactly(0), postRequestedFor(urlPathMatching("/bot.*/sendMessage")));
    }

    @Test
    void contaAtivaContinuaRecebendoAlerta() {
        Usuario dono = donoComTelegram("ativo@teste.com");
        prepararTalhaoComRajadaExtrema(dono);

        alertaPulverizacaoService.processarTodosTalhoes();

        TELEGRAM_MOCK.verify(postRequestedFor(urlPathMatching("/bot.*/sendMessage")));
    }
}
