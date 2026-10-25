package com.agroclima.api.business.talhao;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.TokenRecuperacaoSenhaRepository;
import com.agroclima.api.business.auth.Usuario;
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
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PropriedadeRepository propriedadeRepository;

    @Autowired
    private TalhaoRepository talhaoRepository;

    private String registrarELogar(String email, String papel) {
        usuarioRepository.save(new Usuario("Usuario Teste", email, passwordEncoder.encode("senha12345"),
                Papel.valueOf(papel)));
        ResponseEntity<Map> loginResposta = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "senha", "senha12345"), Map.class);
        return (String) dados(loginResposta).get("token");
    }

    /** Gestor so nasce por convite (registro publico recusa o papel), entao o teste o grava direto. */
    private String criarGestorELogar(String email) {
        usuarioRepository.save(new Usuario("Gestor Teste", email, passwordEncoder.encode("senha12345"),
                Papel.ADMIN_MASTER));
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
    void talhaoForaDoParaRetorna422SemOpcaoDeConfirmar() {
        String token = registrarELogar("dono2@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(token, "Fazenda Dois");

        // Sao Paulo, bem fora do Para
        ResponseEntity<Map> resposta = criarTalhao(token, propriedadeId, "Talhão Fora", poligono(-46.61, -23.51, 0.01), false);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        Map<String, Object> detalhes = (Map<String, Object>) resposta.getBody().get("detalhes");
        assertThat(detalhes.get("tipo")).isEqualTo("FORA_DO_PARA");
        assertThat(detalhes.get("requer_confirmacao")).isEqualTo(false);
        assertThat((String) resposta.getBody().get("mensagem")).contains("dentro do estado do Pará");
    }

    @Test
    void confirmarNaoLiberaTalhaoForaDoPara() {
        String token = registrarELogar("dono3@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(token, "Fazenda Tres");

        // O campo antigo confirmar_fora_do_para e ignorado: a regra agora e dura.
        ResponseEntity<Map> resposta = criarTalhao(token, propriedadeId, "Talhão Fora Confirmado", poligono(-46.61, -23.51, 0.01), true);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void talhaoNoMaranhaoDentroDaAntigaCaixaRetangularTambemEhRecusado() {
        String token = registrarELogar("dono3b@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(token, "Fazenda Tres B");

        // Imperatriz (MA): cabia na caixa retangular que a regra usava antes.
        ResponseEntity<Map> resposta = criarTalhao(token, propriedadeId, "Talhão MA", poligono(-47.48, -5.52, 0.01), false);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
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

        String tokenGestor = criarGestorELogar("gestor@teste.com");
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

    private ResponseEntity<Map> editarTalhao(String token, String talhaoId, String nome, Map<String, Object> geometria) {
        Map<String, Object> corpo = Map.of("nome", nome, "geometria", geometria);
        return restTemplate.exchange("/api/v1/talhoes/" + talhaoId, HttpMethod.PUT, new HttpEntity<>(corpo, headersCom(token)), Map.class);
    }

    private String criarTalhaoId(String token, String propriedadeId, String nome, Map<String, Object> geometria) {
        return (String) dados(criarTalhao(token, propriedadeId, nome, geometria, false)).get("id");
    }

    @Test
    void editarTrocaNomeEGeometriaERecalculaAArea() {
        String token = registrarELogar("editor1@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(token, "Fazenda Editor Um");
        String talhaoId = criarTalhaoId(token, propriedadeId, "Talhão Antigo", poligono(-48.50, -1.45, 0.01));
        double areaAntes = talhaoRepository.findById(java.util.UUID.fromString(talhaoId)).orElseThrow().getAreaHa();

        ResponseEntity<Map> resposta = editarTalhao(token, talhaoId, "Talhão Novo", poligono(-48.50, -1.45, 0.02));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dados(resposta).get("nome")).isEqualTo("Talhão Novo");
        assertThat((Double) dados(resposta).get("area_ha")).isGreaterThan(areaAntes * 3);
        var salvo = talhaoRepository.findById(java.util.UUID.fromString(talhaoId)).orElseThrow();
        assertThat(salvo.getNome()).isEqualTo("Talhão Novo");
        assertThat(talhaoRepository.findByPropriedadeId(java.util.UUID.fromString(propriedadeId),
                org.springframework.data.domain.Pageable.unpaged())).hasSize(1);
    }

    @Test
    void editarNaoContaOProprioTalhaoComoSobreposicao() {
        String token = registrarELogar("editor2@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(token, "Fazenda Editor Dois");
        String talhaoId = criarTalhaoId(token, propriedadeId, "Talhão", poligono(-48.50, -1.45, 0.01));

        // quase a mesma area: sobrepoe o proprio talhao, o que nao pode ser tratado como conflito
        ResponseEntity<Map> resposta = editarTalhao(token, talhaoId, "Talhão", poligono(-48.499, -1.449, 0.01));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void editarSobrepondoOutroTalhaoDaMesmaPropriedadeDa409() {
        String token = registrarELogar("editor3@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(token, "Fazenda Editor Tres");
        criarTalhaoId(token, propriedadeId, "Talhão Vizinho", poligono(-48.50, -1.45, 0.01));
        String talhaoId = criarTalhaoId(token, propriedadeId, "Talhão Solto", poligono(-48.49, -1.45, 0.005));

        ResponseEntity<Map> resposta = editarTalhao(token, talhaoId, "Talhão Solto", poligono(-48.495, -1.445, 0.01));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<String, Object> detalhes = (Map<String, Object>) resposta.getBody().get("detalhes");
        assertThat(detalhes.get("tipo")).isEqualTo("SOBREPOSICAO");
    }

    @Test
    void editarParaForaDoParaDa422SemConfirmacaoENadaMuda() {
        String token = registrarELogar("editor4@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(token, "Fazenda Editor Quatro");
        String talhaoId = criarTalhaoId(token, propriedadeId, "Talhão Dentro", poligono(-48.50, -1.45, 0.01));

        ResponseEntity<Map> resposta = editarTalhao(token, talhaoId, "Talhão Fora", poligono(-46.61, -23.51, 0.01));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        Map<String, Object> detalhes = (Map<String, Object>) resposta.getBody().get("detalhes");
        assertThat(detalhes.get("tipo")).isEqualTo("FORA_DO_PARA");
        assertThat(talhaoRepository.findById(java.util.UUID.fromString(talhaoId)).orElseThrow().getNome()).isEqualTo("Talhão Dentro");
    }

    @Test
    void editarComGeometriaInvalidaOuNomeGrandeRecusa() {
        String token = registrarELogar("editor5@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(token, "Fazenda Editor Cinco");
        String talhaoId = criarTalhaoId(token, propriedadeId, "Talhão", poligono(-48.50, -1.45, 0.01));

        Map<String, Object> gravata = Map.of("type", "Polygon", "coordinates", List.of(List.of(
                List.of(-48.50, -1.45), List.of(-48.49, -1.44), List.of(-48.49, -1.45), List.of(-48.50, -1.44), List.of(-48.50, -1.45))));
        assertThat(editarTalhao(token, talhaoId, "Talhão", gravata).getStatusCode().is4xxClientError()).isTrue();
        assertThat(editarTalhao(token, talhaoId, "T".repeat(51), poligono(-48.50, -1.45, 0.01)).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void editarTalhaoInexistenteDa404() {
        String token = registrarELogar("editor6@teste.com", "PRODUTOR_RURAL");

        ResponseEntity<Map> resposta = editarTalhao(token, java.util.UUID.randomUUID().toString(), "Talhão", poligono(-48.50, -1.45, 0.01));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void editarTalhaoDeOutroProdutorEAgronomoSaoRecusados() {
        String tokenDono = registrarELogar("editor7@teste.com", "PRODUTOR_RURAL");
        String propriedadeId = criarPropriedade(tokenDono, "Fazenda Editor Sete");
        String talhaoId = criarTalhaoId(tokenDono, propriedadeId, "Talhão do Dono", poligono(-48.50, -1.45, 0.01));

        String tokenOutro = registrarELogar("editor8@teste.com", "PRODUTOR_RURAL");
        String tokenAgronomo = registrarELogar("editor9@teste.com", "AGRONOMO");

        for (String intruso : List.of(tokenOutro, tokenAgronomo)) {
            HttpStatus status = (HttpStatus) editarTalhao(intruso, talhaoId, "Invadido", poligono(-48.50, -1.45, 0.01)).getStatusCode();
            assertThat(status).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        }
        assertThat(talhaoRepository.findById(java.util.UUID.fromString(talhaoId)).orElseThrow().getNome()).isEqualTo("Talhão do Dono");
    }

    @Test
    void editarSemTokenDa401() {
        ResponseEntity<Map> resposta = restTemplate.exchange("/api/v1/talhoes/" + java.util.UUID.randomUUID(), HttpMethod.PUT,
                new HttpEntity<>(Map.of("nome", "x", "geometria", poligono(-48.50, -1.45, 0.01))), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
