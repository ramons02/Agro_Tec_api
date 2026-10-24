package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.StatusConta;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.grupo.GrupoRepository;
import com.agroclima.api.support.GruposDeTeste;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** US2 (spec 022): confirmar e recusar cadastros pendentes; a senha temporaria aparece uma unica vez. */
class ConfirmarRecusarIT extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private GrupoRepository grupoRepository;

    @Autowired
    private AuditoriaContaRepository auditoriaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private com.agroclima.api.business.grupo.Grupo grupoInicial;
    private Usuario gestor;
    private String tokenGestor;

    @BeforeEach
    void preparar() {
        gestor = usuarioRepository.save(
                new Usuario("Gestor", "gestor@teste.com", passwordEncoder.encode("senha12345"), Papel.ADMIN_MASTER));
        grupoInicial = GruposDeTeste.criarGrupo(grupoRepository, usuarioRepository, "Grupo inicial", gestor);
        tokenGestor = login("gestor@teste.com", "senha12345");
    }

    @SuppressWarnings("unchecked")
    private String login(String email, String senha) {
        ResponseEntity<Map> resposta = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "senha", senha), Map.class);
        return (String) ((Map<String, Object>) resposta.getBody().get("dados")).get("token");
    }

    private ResponseEntity<Map> chamar(HttpMethod metodo, String caminho, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(caminho, metodo, new HttpEntity<>(headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dados(ResponseEntity<Map> resposta) {
        return (Map<String, Object>) resposta.getBody().get("dados");
    }

    private Usuario pendente(String email) {
        // Como se tivesse chegado pelo link do grupo: o Master so confirma pendente sem grupo escolhendo um.
        Usuario pendente = Usuario.pendente("Pessoa " + email, email, Papel.PRODUTOR_RURAL, Instant.now());
        pendente.definirGrupo(grupoInicial.getId());
        return usuarioRepository.save(pendente);
    }

    @Test
    void confirmarDevolveSenhaTemporariaUmaVezEAtivaAContaComTrocaObrigatoria() {
        Usuario pendente = pendente("novo@teste.com");

        ResponseEntity<Map> resposta = chamar(HttpMethod.POST, "/api/v1/usuarios/" + pendente.getId() + "/confirmar", tokenGestor);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-store");
        String senha = (String) dados(resposta).get("senha_temporaria");
        assertThat(senha).hasSizeGreaterThanOrEqualTo(12);
        assertThat(dados(resposta)).containsEntry("nome", pendente.getNome()).containsEntry("email", "novo@teste.com");
        Usuario depois = usuarioRepository.findById(pendente.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusConta.ATIVA);
        assertThat(depois.isPrecisaTrocarSenha()).isTrue();
        assertThat(depois.getSenhaHash()).isNotEqualTo(senha);
        assertThat(passwordEncoder.matches(senha, depois.getSenhaHash())).isTrue();
        assertThat(depois.getSenhaTemporariaExpiraEm())
                .isBetween(Instant.now().plus(Duration.ofDays(7)).minusSeconds(60), Instant.now().plus(Duration.ofDays(7)).plusSeconds(60));
    }

    @Test
    void senhaTemporariaNaoApareceEmNenhumaConsultaPosterior() {
        Usuario pendente = pendente("sigilo@teste.com");
        String senha = (String) dados(chamar(HttpMethod.POST,
                "/api/v1/usuarios/" + pendente.getId() + "/confirmar", tokenGestor)).get("senha_temporaria");

        String lista = chamar(HttpMethod.GET, "/api/v1/usuarios", tokenGestor).getBody().toString();

        assertThat(lista).doesNotContain(senha).doesNotContainIgnoringCase("senha_hash").doesNotContain("$2a$");
    }

    @Test
    void segundoConfirmarDa409SemGerarNovaSenha() {
        Usuario pendente = pendente("duplo@teste.com");
        chamar(HttpMethod.POST, "/api/v1/usuarios/" + pendente.getId() + "/confirmar", tokenGestor);
        String hashDepoisDoPrimeiro = usuarioRepository.findById(pendente.getId()).orElseThrow().getSenhaHash();

        ResponseEntity<Map> segundo = chamar(HttpMethod.POST, "/api/v1/usuarios/" + pendente.getId() + "/confirmar", tokenGestor);

        assertThat(segundo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(usuarioRepository.findById(pendente.getId()).orElseThrow().getSenhaHash()).isEqualTo(hashDepoisDoPrimeiro);
        assertThat(auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(pendente.getId())).hasSize(1);
    }

    @Test
    void confirmarGravaAuditoriaSemASenha() {
        Usuario pendente = pendente("audit@teste.com");
        String senha = (String) dados(chamar(HttpMethod.POST,
                "/api/v1/usuarios/" + pendente.getId() + "/confirmar", tokenGestor)).get("senha_temporaria");

        List<AuditoriaConta> trilha = auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(pendente.getId());

        assertThat(trilha).singleElement().satisfies(a -> {
            assertThat(a.getAcao()).isEqualTo(AcaoAuditoria.CONFIRMAR);
            assertThat(a.getAtorId()).isEqualTo(gestor.getId());
            assertThat(a.getAlvoEmail()).isEqualTo("audit@teste.com");
            assertThat(String.valueOf(a.getDetalhe())).doesNotContain(senha);
        });
    }

    @Test
    void confirmarContaInexistenteDa404() {
        ResponseEntity<Map> resposta = chamar(HttpMethod.POST,
                "/api/v1/usuarios/" + java.util.UUID.randomUUID() + "/confirmar", tokenGestor);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void recusarApagaSoContaPendenteEGuardaOEmailNaAuditoria() {
        Usuario pendente = pendente("recusada@teste.com");

        ResponseEntity<Map> resposta = chamar(HttpMethod.DELETE, "/api/v1/usuarios/" + pendente.getId(), tokenGestor);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(usuarioRepository.findById(pendente.getId())).isEmpty();
        List<AuditoriaConta> trilha = auditoriaRepository.findByAlvoEmailIgnoreCaseOrderByCriadoEmDesc("recusada@teste.com");
        assertThat(trilha).singleElement().satisfies(a -> {
            assertThat(a.getAcao()).isEqualTo(AcaoAuditoria.RECUSAR);
            assertThat(a.getAtorId()).isEqualTo(gestor.getId());
            assertThat(a.getAlvoId()).isNull();
        });
    }

    @Test
    void recusarContaQueNaoEstaPendenteDa409() {
        Usuario ativa = usuarioRepository.save(
                new Usuario("Ativa", "ativa@teste.com", passwordEncoder.encode("senha12345"), Papel.PRODUTOR_RURAL));

        ResponseEntity<Map> resposta = chamar(HttpMethod.DELETE, "/api/v1/usuarios/" + ativa.getId(), tokenGestor);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(usuarioRepository.findById(ativa.getId())).isPresent();
    }

    @Test
    void emailRecusadoFicaLivreParaNovoCadastro() {
        Usuario pendente = pendente("volta@teste.com");
        chamar(HttpMethod.DELETE, "/api/v1/usuarios/" + pendente.getId(), tokenGestor);

        ResponseEntity<Map> novo = restTemplate.postForEntity("/api/v1/auth/registro",
                Map.of("nome", "Volta", "email", "volta@teste.com", "papel", "PRODUTOR_RURAL"), Map.class);

        assertThat(novo.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void resumoTrazPendentesEListaFiltraPorStatus() {
        pendente("p1@teste.com");
        pendente("p2@teste.com");

        Map<String, Object> resumo = dados(chamar(HttpMethod.GET, "/api/v1/usuarios/resumo", tokenGestor));
        ResponseEntity<Map> lista = chamar(HttpMethod.GET, "/api/v1/usuarios?status=PENDENTE", tokenGestor);

        assertThat(((Number) resumo.get("pendentes")).longValue()).isEqualTo(2);
        assertThat(((Number) dados(lista).get("total")).longValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itens = (List<Map<String, Object>>) dados(lista).get("itens");
        assertThat(itens).allSatisfy(i -> assertThat(i).containsEntry("status", "PENDENTE"));
    }

    @Test
    void statusInvalidoNaListaDa400() {
        assertThat(chamar(HttpMethod.GET, "/api/v1/usuarios?status=BANIDA", tokenGestor).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void produtorNaoConfirmaNemRecusa() {
        usuarioRepository.save(new Usuario("Prod", "prod@teste.com", passwordEncoder.encode("senha12345"), Papel.PRODUTOR_RURAL));
        String tokenProdutor = login("prod@teste.com", "senha12345");
        Usuario pendente = pendente("alvo@teste.com");

        assertThat(chamar(HttpMethod.POST, "/api/v1/usuarios/" + pendente.getId() + "/confirmar", tokenProdutor).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.DELETE, "/api/v1/usuarios/" + pendente.getId(), tokenProdutor).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(usuarioRepository.findById(pendente.getId()).orElseThrow().getStatus()).isEqualTo(StatusConta.PENDENTE);
    }
}
