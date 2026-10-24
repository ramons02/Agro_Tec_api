package com.agroclima.api.business.mapa;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.talhao.Talhao;
import com.agroclima.api.business.talhao.TalhaoRepository;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Espelha tests/integration/test_mapa_dados_integration.py. */
class MapaControllerIT extends IntegrationTestBase {

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

    private Polygon quadrado(double lon0, double lat0) {
        return GeometriaUtil.FACTORY_4326.createPolygon(new Coordinate[] {
                new Coordinate(lon0, lat0),
                new Coordinate(lon0 + 0.01, lat0),
                new Coordinate(lon0 + 0.01, lat0 + 0.01),
                new Coordinate(lon0, lat0 + 0.01),
                new Coordinate(lon0, lat0),
        });
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    @SuppressWarnings("unchecked")
    void dadosDoMapaTrazPropriedadesComTalhoesSemEstacoes() {
        Usuario dono = usuarioRepository.save(new Usuario("Dono Mapa", "dono-mapa@teste.com", "hash-irrelevante", Papel.PRODUTOR_RURAL));
        String token = jwtService.criarToken(dono.getId(), dono.getPapel()).token();

        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda Mapa", "Belém", dono.getId(), null));
        talhaoRepository.save(new Talhao(propriedade.getId(), "Talhão Mapa",
                GeometriaUtil.normalizarParaMultiPolygon(quadrado(-48.5, -1.45)), 50.0));

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/mapa/dados", HttpMethod.GET, new HttpEntity<>(headers(token)), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = (Map<String, Object>) resposta.getBody().get("dados");
        var propriedades = (java.util.List<Map<String, Object>>) dados.get("propriedades");
        assertThat(propriedades).hasSize(1);
        assertThat(propriedades.get(0).get("nome")).isEqualTo("Fazenda Mapa");
        var talhoes = (java.util.List<Map<String, Object>>) propriedades.get(0).get("talhoes");
        assertThat(talhoes).hasSize(1);
        assertThat(talhoes.get(0).get("geometria_geojson")).isNotNull();
        // Sem INMET nao ha estacoes: o campo saiu do contrato.
        assertThat(dados).doesNotContainKey("estacoes");
    }

    @Test
    @SuppressWarnings("unchecked")
    void produtorSoVeAsPropriasPropriedadesNoMapa() {
        Usuario dono = usuarioRepository.save(new Usuario("Dono A", "dono-a-mapa@teste.com", "hash-irrelevante", Papel.PRODUTOR_RURAL));
        Usuario outro = usuarioRepository.save(new Usuario("Dono B", "dono-b-mapa@teste.com", "hash-irrelevante", Papel.PRODUTOR_RURAL));
        propriedadeRepository.save(new Propriedade("Fazenda de A", "Belém", dono.getId(), null));
        propriedadeRepository.save(new Propriedade("Fazenda de B", "Belém", outro.getId(), null));
        String tokenA = jwtService.criarToken(dono.getId(), dono.getPapel()).token();

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/mapa/dados", HttpMethod.GET, new HttpEntity<>(headers(tokenA)), Map.class);

        var propriedades = (java.util.List<Map<String, Object>>) ((Map<String, Object>) resposta.getBody().get("dados")).get("propriedades");
        assertThat(propriedades).extracting(p -> p.get("nome")).containsExactly("Fazenda de A");
    }
}
