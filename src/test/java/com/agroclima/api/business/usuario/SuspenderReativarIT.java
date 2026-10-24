package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.StatusConta;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** US4 (spec 022): suspender derruba a conta na hora; reativar volta sem trocar a senha. */
class SuspenderReativarIT extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AuditoriaContaRepository auditoriaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario gestor;
    private String tokenGestor;

    @BeforeEach
    void preparar() {
        gestor = conta("gestor@teste.com", Papel.GESTOR_TECNOLOGIA);
        tokenGestor = login("gestor@teste.com");
    }

    private Usuario conta(String email, Papel papel) {
        return usuarioRepository.save(new Usuario("Conta " + email, email, passwordEncoder.encode("senha12345"), papel));
    }

    private ResponseEntity<Map> tentarLogin(String email) {
        return restTemplate.postForEntity("/api/v1/auth/login", Map.of("email", email, "senha", "senha12345"), Map.class);
    }

    @SuppressWarnings("unchecked")
    private String login(String email) {
        return (String) ((Map<String, Object>) tentarLogin(email).getBody().get("dados")).get("token");
    }

    private ResponseEntity<Map> chamar(HttpMethod metodo, String caminho, String token, Object corpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (corpo != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(caminho, metodo, new HttpEntity<>(corpo, headers), Map.class);
    }

    private ResponseEntity<Map> suspender(Usuario alvo, String motivo) {
        return chamar(HttpMethod.POST, "/api/v1/usuarios/" + alvo.getId() + "/suspender", tokenGestor,
                motivo == null ? null : Map.of("motivo", motivo));
    }

    private ResponseEntity<Map> reativar(Usuario alvo) {
        return chamar(HttpMethod.POST, "/api/v1/usuarios/" + alvo.getId() + "/reativar", tokenGestor, null);
    }

    @Test
    void suspenderComMotivoGravaEstadoEAuditoria() {
        Usuario produtor = conta("prod@teste.com", Papel.PRODUTOR_RURAL);

        ResponseEntity<Map> resposta = suspender(produtor, "mensalidade de outubro");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Usuario depois = usuarioRepository.findById(produtor.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusConta.SUSPENSA);
        assertThat(depois.getMotivoSuspensao()).isEqualTo("mensalidade de outubro");
        assertThat(depois.getSuspensaEm()).isNotNull();
        List<AuditoriaConta> trilha = auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(produtor.getId());
        assertThat(trilha).singleElement().satisfies(a -> {
            assertThat(a.getAcao()).isEqualTo(AcaoAuditoria.SUSPENDER);
            assertThat(a.getAtorId()).isEqualTo(gestor.getId());
            assertThat(a.getDetalhe()).isEqualTo("mensalidade de outubro");
        });
    }

    @Test
    void suspenderSemMotivoFunciona() {
        Usuario produtor = conta("semmotivo@teste.com", Papel.PRODUTOR_RURAL);

        assertThat(suspender(produtor, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(usuarioRepository.findById(produtor.getId()).orElseThrow().getMotivoSuspensao()).isNull();
    }

    @Test
    void sessaoAbertaRecebe403NaRequisicaoSeguinteELoginNovoDa403AposSenhaCorreta() {
        Usuario produtor = conta("logado@teste.com", Papel.PRODUTOR_RURAL);
        String tokenProdutor = login("logado@teste.com");
        assertThat(chamar(HttpMethod.GET, "/api/v1/auth/me", tokenProdutor, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        suspender(produtor, null);

        ResponseEntity<Map> aberta = chamar(HttpMethod.GET, "/api/v1/auth/me", tokenProdutor, null);
        assertThat(aberta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(String.valueOf(aberta.getBody().get("mensagem"))).contains("suspensa");
        ResponseEntity<Map> novoLogin = tentarLogin("logado@teste.com");
        assertThat(novoLogin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(String.valueOf(novoLogin.getBody().get("mensagem"))).contains("suspensa");
    }

    @Test
    void loginComSenhaErradaDeContaSuspensaNaoRevelaOEstado() {
        Usuario produtor = conta("sigilo@teste.com", Papel.PRODUTOR_RURAL);
        suspender(produtor, null);

        ResponseEntity<Map> resposta = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "sigilo@teste.com", "senha", "senhaErrada1"), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Usuário ou senha inválidos.");
    }

    @Test
    void reativarVoltaSemExigirTrocaDeSenhaEOsDadosFicamIntactos() {
        Usuario produtor = conta("volta@teste.com", Papel.PRODUTOR_RURAL);
        String hashAntes = produtor.getSenhaHash();
        suspender(produtor, "atraso");

        ResponseEntity<Map> resposta = reativar(produtor);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Usuario depois = usuarioRepository.findById(produtor.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusConta.ATIVA);
        assertThat(depois.getMotivoSuspensao()).isNull();
        assertThat(depois.isPrecisaTrocarSenha()).isFalse();
        assertThat(depois.getSenhaHash()).isEqualTo(hashAntes);
        assertThat(tentarLogin("volta@teste.com").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(produtor.getId()))
                .extracting(AuditoriaConta::getAcao)
                .containsExactly(AcaoAuditoria.REATIVAR, AcaoAuditoria.SUSPENDER);
    }

    @Test
    void naoSuspendeASiMesmo() {
        ResponseEntity<Map> resposta = suspender(gestor, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(usuarioRepository.findById(gestor.getId()).orElseThrow().getStatus()).isEqualTo(StatusConta.ATIVA);
    }

    @Test
    void naoSuspendeOUltimoGestorAtivo() {
        Usuario outroGestor = conta("gestor2@teste.com", Papel.GESTOR_TECNOLOGIA);
        String tokenOutro = login("gestor2@teste.com");
        // Dois gestores ativos: o primeiro pode suspender o segundo.
        assertThat(suspender(outroGestor, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        reativar(outroGestor);
        // O segundo suspende o primeiro; sobra so ele, e ele nao pode suspender a si mesmo nem o outro de volta.
        assertThat(chamar(HttpMethod.POST, "/api/v1/usuarios/" + gestor.getId() + "/suspender", tokenOutro, null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> resposta = chamar(HttpMethod.POST,
                "/api/v1/usuarios/" + outroGestor.getId() + "/suspender", tokenOutro, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(usuarioRepository.countByPapelAndStatus(Papel.GESTOR_TECNOLOGIA, StatusConta.ATIVA)).isEqualTo(1);
    }

    @Test
    void suspenderContaPendenteOuJaSuspensaDa409EReativarContaAtivaDa409() {
        Usuario pendente = usuarioRepository.save(
                Usuario.pendente("Pend", "pend@teste.com", Papel.PRODUTOR_RURAL, java.time.Instant.now()));
        Usuario produtor = conta("ja@teste.com", Papel.PRODUTOR_RURAL);
        suspender(produtor, null);

        assertThat(suspender(pendente, null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(suspender(produtor, null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reativar(gestor).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void produtorNaoSuspendeNemReativa() {
        conta("prod2@teste.com", Papel.PRODUTOR_RURAL);
        Usuario alvo = conta("alvo@teste.com", Papel.PRODUTOR_RURAL);
        String tokenProdutor = login("prod2@teste.com");

        assertThat(chamar(HttpMethod.POST, "/api/v1/usuarios/" + alvo.getId() + "/suspender", tokenProdutor, null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.POST, "/api/v1/usuarios/" + alvo.getId() + "/reativar", tokenProdutor, null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
