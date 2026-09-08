package com.agroclima.api.business.talhao;

import com.agroclima.api.business.auth.TokenRecuperacaoSenhaRepository;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Espelha tests/integration/test_propriedades_talhoes_integration.py. */
class TalhaoControllerIT extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TokenRecuperacaoSenhaRepository tokenRepository;

    @Autowired
    private PropriedadeRepository propriedadeRepository;

    @Autowired
    private TalhaoRepository talhaoRepository;

    private String registrarELogar(String email, String papel) {
        restTemplate.postForEntity("/api/v1/auth/registro",
                Map.of("nome", "Usuario Teste", "email", email, "senha", "senha12345", "papel", papel), Map.class);
        ResponseEntity<Map> loginResposta = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "senha", "senha12345"), Map.class);
        return (String) dados(loginResposta).get("token");
    }

    private HttpHeaders headersCom(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dados(ResponseEntity<Map> resposta) {
        return (Map<String, Object>) resposta.getBody().get("dados");
    }

    private String criarPropriedade(String token, String nome) {
        ResponseEntity<Map> resposta = restTemplate.exchange("/api/v1/propriedades", HttpMethod.POST,
                new HttpEntity<>(Map.of("nome", nome, "municipio", "Belém"), headersCom(token)), Map.class);
        return (String) dados(resposta).get("id");
    }

    private Map<String, Object> poligono(double lon0, double lat0, double lado) {
        List<List<Double>> anel = List.of(
                List.of(lon0, lat0),
                List.of(lon0 + lado, lat0),
                List.of(lon0 + lado, lat0 + lado),
                List.of(lon0, lat0 + lado),
                List.of(lon0, lat0));
        return Map.of("type", "Polygon", "coordinates", List.of(anel));
    }

    private ResponseEntity<Map> criarTalhao(
            String token, String propriedadeId, String nome, Map<String, Object> geometria, boolean confirmarForaDoPara) {
        Map<String, Object> corpo = Map.of(
                "propriedade_id", propriedadeId,
                "nome", nome,
                "geometria", geometria,
                "confirmar_fora_do_para", confirmarForaDoPara);
        return restTemplate.exchange("/api/v1/talhoes", HttpMethod.POST, new HttpEntity<>(corpo, headersCom(token)), Map.class);
    }

    @Test
    void criaTalhaoDentroDoParaComSucessoECalculaArea() {
        String token = registrarELogar("dono1@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(token, "Fazenda Um");

        ResponseEntity<Map> resposta = criarTalhao(token, propriedadeId, "Talhão A", poligono(-48.50, -1.45, 0.01), false);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = dados(resposta);
        assertThat(dados.get("nome")).isEqualTo("Talhão A");
        assertThat((Double) dados.get("area_ha")).isGreaterThan(0.0);
        assertThat(dados.get("aviso")).isNull();
    }

    @Test
    void talhaoForaDoParaSemConfirmacaoRetorna422ComRequerConfirmacao() {
        String token = registrarELogar("dono2@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(token, "Fazenda Dois");

        // Sao Paulo, bem fora da bbox do Para
        ResponseEntity<Map> resposta = criarTalhao(token, propriedadeId, "Talhão Fora", poligono(-46.61, -23.51, 0.01), false);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        Map<String, Object> detalhes = (Map<String, Object>) resposta.getBody().get("detalhes");
        assertThat(detalhes.get("tipo")).isEqualTo("FORA_DO_PARA");
        assertThat(detalhes.get("requer_confirmacao")).isEqualTo(true);
    }

    @Test
    void talhaoForaDoParaComConfirmacaoEAceito() {
        String token = registrarELogar("dono3@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(token, "Fazenda Tres");

        ResponseEntity<Map> resposta = criarTalhao(token, propriedadeId, "Talhão Fora Confirmado", poligono(-46.61, -23.51, 0.01), true);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void sobreposicaoNaMesmaPropriedadeBloqueiaCom409() {
        String token = registrarELogar("dono4@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(token, "Fazenda Quatro");
        criarTalhao(token, propriedadeId, "Talhão Base", poligono(-48.50, -1.45, 0.01), false);

        // deslocado pra sobrepor metade da area do talhao base
        ResponseEntity<Map> resposta = criarTalhao(token, propriedadeId, "Talhão Sobreposto", poligono(-48.495, -1.445, 0.01), false);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<String, Object> detalhes = (Map<String, Object>) resposta.getBody().get("detalhes");
        assertThat(detalhes.get("tipo")).isEqualTo("SOBREPOSICAO");
    }

    @Test
    void sobreposicaoComOutraPropriedadeNaoBloqueiaMasAvisa() {
        String token = registrarELogar("dono5@teste.com", "PRODUTOR_RURAL");
        String propriedadeA = criarPropriedade(token, "Fazenda Cinco A");
        String propriedadeB = criarPropriedade(token, "Fazenda Cinco B");
        criarTalhao(token, propriedadeA, "Talhão A", poligono(-48.50, -1.45, 0.01), false);

        ResponseEntity<Map> resposta = criarTalhao(token, propriedadeB, "Talhão B", poligono(-48.495, -1.445, 0.01), false);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) dados(resposta).get("aviso")).contains("possível divisa em disputa");
    }

    @Test
    void agronomoNaoConseguePropriedade403() {
        String token = registrarELogar("agronomo1@teste.com", "AGRONOMO");
        ResponseEntity<Map> resposta = restTemplate.exchange("/api/v1/propriedades", HttpMethod.POST,
                new HttpEntity<>(Map.of("nome", "Fazenda Agronomo"), headersCom(token)), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void produtorNaoConsegueCriarTalhaoEmPropriedadeDeOutro() {
        String tokenDono = registrarELogar("donoreal@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(tokenDono, "Fazenda do Dono Real");

        String tokenOutro = registrarELogar("intruso@teste.com", "PRODUTOR_RURAL");
        ResponseEntity<Map> resposta = criarTalhao(tokenOutro, propriedadeId, "Talhão Invasor", poligono(-48.50, -1.45, 0.01), false);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void propriedadeInvisivelRetorna404NuncaA403NaLeitura() {
        String tokenDono = registrarELogar("donoprivado@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(tokenDono, "Fazenda Privada");

        String tokenOutro = registrarELogar("estranho@teste.com", "PRODUTOR_RURAL");
        ResponseEntity<Map> resposta = restTemplate.exchange("/api/v1/propriedades/" + propriedadeId, HttpMethod.GET,
                new HttpEntity<>(headersCom(tokenOutro)), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void gestorTecnologiaVeTudoIndependenteDeDono() {
        String tokenDono = registrarELogar("donoY@teste.com", "PRODUTOR_RURAL");
        criarPropriedade(tokenDono, "Fazenda Y");

        String tokenGestor = registrarELogar("gestor@teste.com", "GESTOR_TECNOLOGIA");
        ResponseEntity<Map> resposta = restTemplate.exchange("/api/v1/propriedades?page=1&page_size=50", HttpMethod.GET,
                new HttpEntity<>(headersCom(tokenGestor)), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = dados(resposta);
        assertThat(((Number) dados.get("total")).intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void geometriaInvalidaRetorna422() {
        String token = registrarELogar("geominvalida@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(token, "Fazenda Geo Invalida");

        // "gravata borboleta" -- auto-intersectante
        Map<String, Object> bowtie = Map.of("type", "Polygon", "coordinates", List.of(List.of(
                List.of(-48.50, -1.45), List.of(-48.49, -1.44), List.of(-48.49, -1.45),
                List.of(-48.50, -1.44), List.of(-48.50, -1.45))));

        ResponseEntity<Map> resposta = criarTalhao(token, propriedadeId, "Talhão Bowtie", bowtie, false);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void excluiTalhaoComSucesso() {
        String token = registrarELogar("exclui@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(token, "Fazenda Exclui");
        ResponseEntity<Map> criado = criarTalhao(token, propriedadeId, "Talhão a Excluir", poligono(-48.50, -1.45, 0.01), false);
        String talhaoId = (String) dados(criado).get("id");

        ResponseEntity<Void> resposta = restTemplate.exchange("/api/v1/talhoes/" + talhaoId, HttpMethod.DELETE,
                new HttpEntity<>(headersCom(token)), Void.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(talhaoRepository.findById(java.util.UUID.fromString(talhaoId))).isEmpty();
    }
}
