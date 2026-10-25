package com.agroclima.api.isolamento;

import com.agroclima.api.business.propriedade.Propriedade;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** O Master enxerga todos os grupos juntos; cada propriedade traz o nome do grupo para ele saber de quem e. */
class GrupoDaPropriedadeIT extends IsolamentoBase {

    @SuppressWarnings("unchecked")
    private Map<String, String> grupoPorPropriedade(ResponseEntity<Map> resposta) {
        List<Map<String, Object>> itens = (List<Map<String, Object>>) dados(resposta).get("itens");
        return itens.stream().collect(Collectors.toMap(
                i -> (String) i.get("nome"),
                i -> i.get("grupo") == null ? "(sem grupo)" : (String) ((Map<String, Object>) i.get("grupo")).get("nome")));
    }

    @Test
    void masterVeOGrupoDeCadaPropriedade() {
        Map<String, String> grupos = grupoPorPropriedade(chamar(HttpMethod.GET, "/api/v1/propriedades", master, null));

        assertThat(grupos).containsEntry("Fazenda A", "Grupo A").containsEntry("Fazenda B", "Grupo B");
    }

    @Test
    void adminEProdutorSoVeemOProprioGrupo() {
        for (var quem : List.of(adminA, produtorA)) {
            Map<String, String> grupos = grupoPorPropriedade(chamar(HttpMethod.GET, "/api/v1/propriedades", quem, null));
            assertThat(grupos).as(quem.getEmail()).containsOnlyKeys("Fazenda A").containsEntry("Fazenda A", "Grupo A");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void buscarPorIdETambemCriarTrazemOGrupo() {
        Map<String, Object> grupoDaBusca = (Map<String, Object>) dados(
                chamar(HttpMethod.GET, "/api/v1/propriedades/" + propriedadeB.getId(), master, null)).get("grupo");
        assertThat(grupoDaBusca.get("nome")).isEqualTo("Grupo B");
        assertThat(grupoDaBusca.get("id")).isEqualTo(grupoB.getId().toString());

        ResponseEntity<Map> criada = chamar(HttpMethod.POST, "/api/v1/propriedades", adminA,
                Map.of("nome", "Fazenda Nova", "municipio", "Belém"));
        assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) dados(criada).get("grupo")).get("nome")).isEqualTo("Grupo A");
    }

    @Test
    void propriedadeSemGrupoVemComGrupoNulo() {
        Propriedade orfa = propriedadeRepository.save(new Propriedade("Fazenda Orfa", "Belém", produtorA.getId(), null));

        Map<String, String> grupos = grupoPorPropriedade(chamar(HttpMethod.GET, "/api/v1/propriedades", master, null));

        assertThat(orfa.getGrupoId()).isNull();
        assertThat(grupos).containsEntry("Fazenda Orfa", "(sem grupo)");
    }
}
