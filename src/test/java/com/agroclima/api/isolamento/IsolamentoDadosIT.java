package com.agroclima.api.isolamento;

import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.core.security.EstadoContaCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolamento entre grupos nas rotas de DADOS (spec 024, FR-018, SC-001): quem e do grupo A nunca le, altera nem
 * apaga dado do grupo B, por nenhuma rota. As rotas que chamam fontes externas (pulverizacao, recomendacao,
 * balanco, clima) so sao chamadas como intruso: a recusa vem antes de qualquer consulta externa.
 */
class IsolamentoDadosIT extends IsolamentoBase {

    @Autowired
    private EstadoContaCache estadoContaCache;

    @Autowired
    private JdbcTemplate jdbc;

    private List<Usuario> intrusosDoGrupoA() {
        return List.of(adminA, produtorA, agronomoA);
    }

    private static final String GEOJSON_POLIGONO = "{\"type\":\"Polygon\",\"coordinates\":[[[-47.30,-1.30],[-47.29,-1.30],"
            + "[-47.29,-1.29],[-47.30,-1.29],[-47.30,-1.30]]]}";

    private void assertRecusado(ResponseEntity<?> resposta, String rota, Usuario quem) {
        assertThat(resposta.getStatusCode().value())
                .as("%s como %s deveria ser recusado (403 ou 404)", rota, quem.getEmail())
                .isIn(403, 404);
    }

    @Test
    void leituraPorIdDeOutroGrupoNaoEncontraNada() {
        List<String> rotas = List.of(
                "/api/v1/propriedades/" + propriedadeB.getId(),
                "/api/v1/talhoes/" + talhaoB.getId(),
                "/api/v1/talhoes/" + talhaoB.getId() + "/solo",
                "/api/v1/talhoes/" + talhaoB.getId() + "/pulverizacao",
                "/api/v1/talhoes/" + talhaoB.getId() + "/recomendacao",
                "/api/v1/talhoes/" + talhaoB.getId() + "/balanco-hidrico",
                "/api/v1/talhoes/" + talhaoB.getId() + "/balanco-hidrico/historico",
                "/api/v1/clima/atual?talhao_id=" + talhaoB.getId());

        for (Usuario intruso : intrusosDoGrupoA()) {
            for (String rota : rotas) {
                ResponseEntity<Map> resposta = chamar(HttpMethod.GET, rota, intruso, null);
                assertRecusado(resposta, rota, intruso);
                assertThat(String.valueOf(resposta.getBody())).doesNotContain("Fazenda B").doesNotContain("Talhao B");
            }
        }
    }

    @Test
    void listasDoGrupoANaoTrazemNadaDoGrupoB() {
        List<String> rotas = List.of(
                "/api/v1/propriedades", "/api/v1/talhoes", "/api/v1/mapa/dados", "/api/v1/dashboard/plantio");

        for (Usuario usuario : intrusosDoGrupoA()) {
            for (String rota : rotas) {
                String corpo = chamar(HttpMethod.GET, rota, usuario, null).getBody().toString();
                assertThat(corpo).as("%s como %s", rota, usuario.getEmail())
                        .doesNotContain("Fazenda B").doesNotContain("Talhao B")
                        .doesNotContain(propriedadeB.getId().toString()).doesNotContain(talhaoB.getId().toString());
            }
            String csv = chamarTexto(HttpMethod.GET, "/api/v1/dashboard/plantio/exportar.csv", usuario).getBody();
            assertThat(csv).as("CSV como %s", usuario.getEmail()).doesNotContain("Fazenda B").doesNotContain("Talhao B");
        }
    }

    @Test
    void listarTalhoesDeUmaPropriedadeDeOutroGrupoDa404() {
        for (Usuario intruso : intrusosDoGrupoA()) {
            ResponseEntity<Map> resposta = chamar(
                    HttpMethod.GET, "/api/v1/talhoes?propriedade_id=" + propriedadeB.getId(), intruso, null);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void escritaEmOutroGrupoERecusadaENadaMuda() {
        for (Usuario intruso : List.of(adminA, produtorA)) {
            assertRecusado(chamar(HttpMethod.PUT, "/api/v1/propriedades/" + propriedadeB.getId(), intruso,
                    Map.of("nome", "Invadida", "municipio", "Belém")), "PUT propriedade", intruso);
            assertRecusado(chamar(HttpMethod.DELETE, "/api/v1/propriedades/" + propriedadeB.getId(), intruso, null),
                    "DELETE propriedade", intruso);
            assertRecusado(chamar(HttpMethod.DELETE, "/api/v1/talhoes/" + talhaoB.getId(), intruso, null),
                    "DELETE talhao", intruso);
            assertRecusado(chamar(HttpMethod.PUT, "/api/v1/talhoes/" + talhaoB.getId(), intruso, Map.of(
                    "nome", "Invadido", "geometria", jsonDe(GEOJSON_POLIGONO))), "PUT talhao", intruso);
            assertRecusado(chamar(HttpMethod.POST, "/api/v1/talhoes", intruso, Map.of(
                    "propriedade_id", propriedadeB.getId().toString(), "nome", "Intruso",
                    "geometria", jsonDe(GEOJSON_POLIGONO))), "POST talhao", intruso);
            assertRecusado(chamar(HttpMethod.POST, "/api/v1/propriedades/" + propriedadeB.getId() + "/vinculos", intruso,
                    Map.of("agronomo_email", agronomoA.getEmail())), "POST vinculo", intruso);
        }
        assertThat(propriedadeRepository.findById(propriedadeB.getId())).get()
                .extracting(p -> p.getNome()).isEqualTo("Fazenda B");
        assertThat(talhaoRepository.findById(talhaoB.getId())).get()
                .extracting(t -> t.getNome()).isEqualTo("Talhao B");
        assertThat(talhaoRepository.findByPropriedadeId(propriedadeB.getId(), org.springframework.data.domain.Pageable.unpaged()))
                .hasSize(1);
    }

    @Test
    void agronomoDeOutroGrupoNaoEConvidadoParaAPropriedadeDoGrupo() {
        ResponseEntity<Map> resposta = chamar(HttpMethod.POST,
                "/api/v1/propriedades/" + propriedadeA.getId() + "/vinculos", adminA,
                Map.of("agronomo_email", agronomoB.getEmail()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void adminVeOsDadosDoGrupoTodoMesmoSemSerODono() {
        ResponseEntity<Map> propriedade = chamar(HttpMethod.GET, "/api/v1/propriedades/" + propriedadeA.getId(), adminA, null);
        ResponseEntity<Map> talhao = chamar(HttpMethod.GET, "/api/v1/talhoes/" + talhaoA.getId(), adminA, null);

        assertThat(propriedade.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(talhao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades", adminA, null).getBody().toString())
                .contains("Fazenda A");
    }

    @Test
    void masterVeOsDoisGrupos() {
        String propriedades = chamar(HttpMethod.GET, "/api/v1/propriedades", master, null).getBody().toString();

        assertThat(propriedades).contains("Fazenda A").contains("Fazenda B");
        assertThat(chamar(HttpMethod.GET, "/api/v1/talhoes/" + talhaoB.getId(), master, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void agronomoSoVeOQueEstaVinculadoEQueEstaNoGrupoDele() {
        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades/" + propriedadeA.getId(), agronomoA, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Sai do grupo A: o vinculo ja aceito deixa de dar acesso (FR-015).
        jdbc.update("DELETE FROM agronomo_grupos WHERE agronomo_id = ?", agronomoA.getId());
        estadoContaCache.invalidar(agronomoA.getId());

        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades/" + propriedadeA.getId(), agronomoA, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void tokenAnteriorAMudancaDeGrupoDeixaDeValerEOEscopoNovoVale() {
        String tokenAntigo = token(produtorA);
        // O Master move o produtor para o grupo B: grupo novo e versao de sessao incrementada.
        jdbc.update("UPDATE usuarios SET grupo_id = ?, versao_sessao = versao_sessao + 1 WHERE id = ?",
                grupoB.getId(), produtorA.getId());
        estadoContaCache.invalidar(produtorA.getId());

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(tokenAntigo);
        ResponseEntity<Map> comTokenAntigo = restTemplate.exchange("/api/v1/propriedades", HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers), Map.class);
        Usuario movido = usuarioRepository.findById(produtorA.getId()).orElseThrow();
        ResponseEntity<Map> comTokenNovo = chamar(HttpMethod.GET, "/api/v1/propriedades", movido, null);

        assertThat(comTokenAntigo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(comTokenNovo.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminSemGrupoNaoVeNada() {
        Usuario semGrupo = conta("admin-sem-grupo@iso.test", com.agroclima.api.business.auth.Papel.GESTOR_TECNOLOGIA);

        String propriedades = chamar(HttpMethod.GET, "/api/v1/propriedades", semGrupo, null).getBody().toString();

        assertThat(propriedades).doesNotContain("Fazenda A").doesNotContain("Fazenda B");
        assertRecusado(chamar(HttpMethod.GET, "/api/v1/talhoes/" + talhaoA.getId(), semGrupo, null), "talhao", semGrupo);
    }

    private Object jsonDe(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
