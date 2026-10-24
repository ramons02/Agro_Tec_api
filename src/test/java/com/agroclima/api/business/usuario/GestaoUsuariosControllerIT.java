package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.vinculo.VinculoAgronomoPropriedade;
import com.agroclima.api.business.vinculo.VinculoAgronomoPropriedadeRepository;
import com.agroclima.api.core.security.JwtService;
import com.agroclima.api.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Monitoramento de contas para o gestor: so ele ve a lista, e a lista nunca expoe senha. */
class GestaoUsuariosControllerIT extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PropriedadeRepository propriedadeRepository;

    @Autowired
    private VinculoAgronomoPropriedadeRepository vinculoRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    private Usuario gestor;
    private Usuario produtorComDuas;
    private Usuario produtorSemPropriedade;
    private Usuario agronomoVinculado;

    @BeforeEach
    void preparar() {
        gestor = usuarioRepository.save(new Usuario("Gestora Central", "gestora@teste.com", "hash-segredo-1", Papel.GESTOR_TECNOLOGIA));
        produtorComDuas = usuarioRepository.save(new Usuario("Maria Produtora", "maria@fazenda.com", "hash-segredo-2", Papel.PRODUTOR_RURAL));
        produtorSemPropriedade = usuarioRepository.save(new Usuario("João Novato", "joao@fazenda.com", "hash-segredo-3", Papel.PRODUTOR_RURAL));
        agronomoVinculado = usuarioRepository.save(new Usuario("Ana Agrônoma", "ana@consultoria.com", "hash-segredo-4", Papel.AGRONOMO));

        Propriedade p1 = propriedadeRepository.save(new Propriedade("Fazenda 1", "Belém", produtorComDuas.getId(), null));
        propriedadeRepository.save(new Propriedade("Fazenda 2", "Belém", produtorComDuas.getId(), null));
        VinculoAgronomoPropriedade aceito = new VinculoAgronomoPropriedade(agronomoVinculado.getId(), p1.getId());
        aceito.aceitar(Instant.now());
        vinculoRepository.save(aceito);
        // Convite ainda nao aceito nao conta.
        vinculoRepository.save(new VinculoAgronomoPropriedade(agronomoVinculado.getId(), propriedadeRepository
                .save(new Propriedade("Fazenda 3", "Belém", produtorSemPropriedade.getId(), null)).getId()));

        // Datas de cadastro conhecidas: gestora ha 60 dias, Maria ha 20, Ana ha 3, Joao agora.
        definirCadastro(gestor, 60);
        definirCadastro(produtorComDuas, 20);
        definirCadastro(agronomoVinculado, 3);
    }

    private void definirCadastro(Usuario usuario, int diasAtras) {
        jdbc.update("UPDATE usuarios SET criado_em = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(diasAtras, ChronoUnit.DAYS)), usuario.getId());
    }

    private HttpHeaders comToken(Usuario usuario) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtService.criarToken(usuario.getId(), usuario.getPapel()).token());
        return headers;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> get(String caminho, Usuario como) {
        HttpHeaders headers = como == null ? new HttpHeaders() : comToken(como);
        return restTemplate.exchange(caminho, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dados(ResponseEntity<Map> resposta) {
        return (Map<String, Object>) resposta.getBody().get("dados");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> itens(ResponseEntity<Map> resposta) {
        return (List<Map<String, Object>>) dados(resposta).get("itens");
    }

    private List<Object> campo(List<Map<String, Object>> itens, String nome) {
        return itens.stream().map(i -> i.get(nome)).toList();
    }

    @Test
    void gestorVeTodasAsContasDaMaisRecenteParaAMaisAntiga() {
        ResponseEntity<Map> resposta = get("/api/v1/usuarios", gestor);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(campo(itens(resposta), "nome"))
                .containsExactly("João Novato", "Ana Agrônoma", "Maria Produtora", "Gestora Central");
        assertThat(((Number) dados(resposta).get("total")).intValue()).isEqualTo(4);
        assertThat(itens(resposta).get(0).get("criado_em")).isNotNull();
    }

    @Test
    void listaNuncaExpoeSenhaOuHash() {
        String corpo = get("/api/v1/usuarios", gestor).getBody().toString();

        // "senha_esquecida_em" (pedido de nova senha) e um campo legitimo; o que nunca sai e a senha em si.
        assertThat(corpo).doesNotContain("hash-segredo").doesNotContain("senha_hash").doesNotContain("senha_temporaria")
                .doesNotContain("$2a$");
    }

    @Test
    void contaPropriedadesDoProdutorEAsVinculadasAceitasDoAgronomo() {
        List<Map<String, Object>> itens = itens(get("/api/v1/usuarios", gestor));

        assertThat(propriedadesDe(itens, "Maria Produtora")).isEqualTo(2);
        assertThat(propriedadesDe(itens, "João Novato")).isEqualTo(1);
        assertThat(propriedadesDe(itens, "Ana Agrônoma")).isEqualTo(1);
        assertThat(propriedadesDe(itens, "Gestora Central")).isZero();
    }

    private int propriedadesDe(List<Map<String, Object>> itens, String nome) {
        return itens.stream().filter(i -> nome.equals(i.get("nome"))).findFirst().map(i -> ((Number) i.get("propriedades")).intValue()).orElseThrow();
    }

    @Test
    void filtraPorPapel() {
        assertThat(campo(itens(get("/api/v1/usuarios?papel=PRODUTOR_RURAL", gestor)), "nome"))
                .containsExactly("João Novato", "Maria Produtora");
        assertThat(campo(itens(get("/api/v1/usuarios?papel=AGRONOMO", gestor)), "nome"))
                .containsExactly("Ana Agrônoma");
    }

    @Test
    void papelInvalidoRetorna400() {
        assertThat(get("/api/v1/usuarios?papel=INEXISTENTE", gestor).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void buscaPorNomeOuEmailSemDiferenciarMaiusculas() {
        assertThat(campo(itens(get("/api/v1/usuarios?q=MARIA", gestor)), "nome")).containsExactly("Maria Produtora");
        assertThat(campo(itens(get("/api/v1/usuarios?q=consultoria", gestor)), "nome")).containsExactly("Ana Agrônoma");
        assertThat(campo(itens(get("/api/v1/usuarios?q=fazenda.com", gestor)), "nome"))
                .containsExactly("João Novato", "Maria Produtora");
    }

    @Test
    void curingasDoLikeSaoTratadosComoTextoLiteral() {
        assertThat(itens(get("/api/v1/usuarios?q=%25", gestor))).isEmpty();
        assertThat(itens(get("/api/v1/usuarios?q=_", gestor))).isEmpty();
    }

    @Test
    void combinaPapelEBusca() {
        assertThat(campo(itens(get("/api/v1/usuarios?papel=PRODUTOR_RURAL&q=joao", gestor)), "nome"))
                .containsExactly("João Novato");
        assertThat(itens(get("/api/v1/usuarios?papel=AGRONOMO&q=joao", gestor))).isEmpty();
    }

    @Test
    void paginaERespeitaOTamanhoMaximo() {
        ResponseEntity<Map> primeira = get("/api/v1/usuarios?page=1&page_size=2", gestor);
        ResponseEntity<Map> segunda = get("/api/v1/usuarios?page=2&page_size=2", gestor);

        assertThat(campo(itens(primeira), "nome")).containsExactly("João Novato", "Ana Agrônoma");
        assertThat(campo(itens(segunda), "nome")).containsExactly("Maria Produtora", "Gestora Central");
        assertThat(((Number) dados(primeira).get("total")).intValue()).isEqualTo(4);
        assertThat(((Number) dados(get("/api/v1/usuarios?page_size=100000", gestor)).get("page_size")).intValue())
                .isEqualTo(100);
    }

    @Test
    void resumoContaPorPapelEOsCadastrosRecentes() {
        ResponseEntity<Map> resposta = get("/api/v1/usuarios/resumo", gestor);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> resumo = dados(resposta);
        assertThat(((Number) resumo.get("total")).intValue()).isEqualTo(4);
        @SuppressWarnings("unchecked")
        Map<String, Object> porPapel = (Map<String, Object>) resumo.get("por_papel");
        assertThat(((Number) porPapel.get("PRODUTOR_RURAL")).intValue()).isEqualTo(2);
        assertThat(((Number) porPapel.get("AGRONOMO")).intValue()).isEqualTo(1);
        assertThat(((Number) porPapel.get("GESTOR_TECNOLOGIA")).intValue()).isEqualTo(1);
        // Ultimos 7 dias: Joao (agora) e Ana (3 dias); ultimos 30: Maria (20 dias) tambem. A gestora (60) fica fora.
        assertThat(((Number) resumo.get("cadastros_semana")).intValue()).isEqualTo(2);
        assertThat(((Number) resumo.get("cadastros_mes")).intValue()).isEqualTo(3);
        assertThat(resumo.get("ultimo_cadastro_em")).isNotNull();
    }

    @Test
    void produtorEAgronomoRecebem403NaListaENoResumo() {
        for (Usuario naoGestor : List.of(produtorComDuas, agronomoVinculado)) {
            assertThat(get("/api/v1/usuarios", naoGestor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(get("/api/v1/usuarios/resumo", naoGestor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void semTokenRetorna401() {
        assertThat(get("/api/v1/usuarios", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/v1/usuarios/resumo", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
