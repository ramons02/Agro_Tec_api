package com.agroclima.api.business.dashboard;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.balancohidrico.BalancoHidricoDiario;
import com.agroclima.api.business.balancohidrico.BalancoHidricoDiarioRepository;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.talhao.Talhao;
import com.agroclima.api.business.talhao.TalhaoRepository;
import com.agroclima.api.core.calculos.StatusPlantio;
import com.agroclima.api.core.geo.GeometriaUtil;
import com.agroclima.api.core.security.JwtService;
import com.agroclima.api.support.IntegrationTestBase;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Espelha tests/integration/test_dashboard_plantio_integration.py. */
class DashboardControllerIT extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PropriedadeRepository propriedadeRepository;

    @Autowired
    private TalhaoRepository talhaoRepository;

    @Autowired
    private BalancoHidricoDiarioRepository balancoHidricoDiarioRepository;

    @Autowired
    private JwtService jwtService;

    private Polygon quadrado(double lon0, double lat0) {
        return GeometriaUtil.FACTORY_4326.createPolygon(new Coordinate[] {
                new Coordinate(lon0, lat0),
                new Coordinate(lon0 + 0.01, lat0),
                new Coordinate(lon0 + 0.01, lat0 + 0.01),
                new Coordinate(lon0, lat0 + 0.01),
                new Coordinate(lon0, lat0),
        });
    }

    private String criarUsuarioEToken(String email, Papel papel) {
        Usuario usuario = usuarioRepository.save(new Usuario("Usuario Dashboard", email, "hash-irrelevante", papel));
        return jwtService.criarToken(usuario.getId(), usuario.getPapel()).token();
    }

    private HttpHeaders headersCom(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void listaTalhoesComStatusDeQuemPossuiPropriedade() {
        String token = criarUsuarioEToken("dono-dash1@teste.com", Papel.PRODUTOR_RURAL);
        Usuario dono = usuarioRepository.findByEmail("dono-dash1@teste.com").orElseThrow();
        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda Dash", "Belém", dono.getId(), null));
        Talhao talhao = talhaoRepository.save(new Talhao(propriedade.getId(), "Talhão Dash",
                GeometriaUtil.normalizarParaMultiPolygon(quadrado(-48.5, -1.45)), 50.0));
        balancoHidricoDiarioRepository.upsert(java.util.UUID.randomUUID(), talhao.getId(), LocalDate.now(),
                20.0, 0.0, 3.0, StatusPlantio.VERMELHO.name());

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/dashboard/plantio?page=1&page_size=20", HttpMethod.GET, new HttpEntity<>(headersCom(token)), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = (Map<String, Object>) resposta.getBody().get("dados");
        var itens = (java.util.List<Map<String, Object>>) dados.get("itens");
        assertThat(itens).hasSize(1);
        assertThat(itens.get(0).get("status_plantio")).isEqualTo("VERMELHO");
        assertThat(itens.get(0).get("propriedade")).isEqualTo("Fazenda Dash");
    }

    @Test
    void outroUsuarioNaoVeTalhaoAlheio() {
        String tokenDono = criarUsuarioEToken("dono-dash2@teste.com", Papel.PRODUTOR_RURAL);
        Usuario dono = usuarioRepository.findByEmail("dono-dash2@teste.com").orElseThrow();
        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda Dash2", "Belém", dono.getId(), null));
        talhaoRepository.save(new Talhao(propriedade.getId(), "Talhão Dash2",
                GeometriaUtil.normalizarParaMultiPolygon(quadrado(-48.6, -1.50)), 30.0));

        String tokenOutro = criarUsuarioEToken("outro-dash2@teste.com", Papel.PRODUTOR_RURAL);
        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/dashboard/plantio", HttpMethod.GET, new HttpEntity<>(headersCom(tokenOutro)), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = (Map<String, Object>) resposta.getBody().get("dados");
        assertThat(((Number) dados.get("total")).intValue()).isEqualTo(0);
    }

    @Test
    void exportaCsvComBomEDelimitadorPontoEVirgula() {
        String token = criarUsuarioEToken("dono-dash3@teste.com", Papel.PRODUTOR_RURAL);
        Usuario dono = usuarioRepository.findByEmail("dono-dash3@teste.com").orElseThrow();
        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda CSV", "Belém", dono.getId(), null));
        talhaoRepository.save(new Talhao(propriedade.getId(), "Talhão CSV",
                GeometriaUtil.normalizarParaMultiPolygon(quadrado(-48.7, -1.55)), 42.5));

        ResponseEntity<String> resposta = restTemplate.exchange(
                "/api/v1/dashboard/plantio/exportar.csv", HttpMethod.GET, new HttpEntity<>(headersCom(token)), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getContentDisposition().getFilename()).isEqualTo("talhoes.csv");
        String corpo = resposta.getBody();
        assertThat(corpo).startsWith("﻿");
        assertThat(corpo).contains("Propriedade;Talhao;Area (ha);Solo;Status;Armazenamento (mm);% da CAD");
        assertThat(corpo).contains("Fazenda CSV;Talhão CSV;42.5;;SEM_CALCULO;;");
    }
}
