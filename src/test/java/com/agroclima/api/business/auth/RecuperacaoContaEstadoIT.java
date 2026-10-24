package com.agroclima.api.business.auth;

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

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Recuperacao de senha por e-mail (HU-13, so gestor) diante dos novos estados da conta (spec 022, edge cases). */
class RecuperacaoContaEstadoIT extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TokenRecuperacaoSenhaRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ResponseEntity<Map> login(String email, String senha) {
        return restTemplate.postForEntity("/api/v1/auth/login", Map.of("email", email, "senha", senha), Map.class);
    }

    private ResponseEntity<Map> recuperar(String email) {
        return restTemplate.postForEntity("/api/v1/auth/recuperar-senha", Map.of("email", email), Map.class);
    }

    private ResponseEntity<Map> redefinir(String token, String novaSenha) {
        return restTemplate.postForEntity("/api/v1/auth/redefinir-senha",
                Map.of("token", token, "nova_senha", novaSenha), Map.class);
    }

    @SuppressWarnings("unchecked")
    private String token(ResponseEntity<Map> login) {
        return (String) ((Map<String, Object>) login.getBody().get("dados")).get("token");
    }

    private Usuario pendente(String email) {
        return usuarioRepository.save(Usuario.pendente("Pendente", email, Papel.PRODUTOR_RURAL, Instant.now()));
    }

    private Usuario ativa(String email, String senha) {
        return usuarioRepository.save(new Usuario("Ativa", email, passwordEncoder.encode(senha), Papel.GESTOR_TECNOLOGIA));
    }

    @Test
    void contaPendenteNaoGeraTokenERespondeIgualAEmailInexistente() {
        pendente("pendente@teste.com");

        ResponseEntity<Map> pendente = recuperar("pendente@teste.com");
        ResponseEntity<Map> inexistente = recuperar("nao-existe@teste.com");

        assertThat(pendente.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pendente.getBody().get("mensagem")).isEqualTo(inexistente.getBody().get("mensagem"));
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void tokenDeContaPendenteEhRecusadoESenhaNaoEhGravada() {
        Usuario pendente = pendente("forjado@teste.com");
        tokenRepository.save(new TokenRecuperacaoSenha(pendente.getId(), "token-forjado-para-pendente"));

        ResponseEntity<Map> resposta = redefinir("token-forjado-para-pendente", "senhaNova123");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Usuario depois = usuarioRepository.findByEmail("forjado@teste.com").orElseThrow();
        assertThat(depois.getSenhaHash()).isNull();
        assertThat(depois.getStatus()).isEqualTo(StatusConta.PENDENTE);
    }

    @Test
    void contaSuspensaRedefineASenhaMasContinuaSemAcesso() {
        Usuario suspensa = ativa("suspensa@teste.com", "senhaAntiga1");
        suspensa.suspender("atraso", Instant.now());
        usuarioRepository.save(suspensa);
        recuperar("suspensa@teste.com");
        String token = tokenRepository.findAll().get(0).getToken();

        ResponseEntity<Map> resposta = redefinir(token, "senhaNova123");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> entrada = login("suspensa@teste.com", "senhaNova123");
        assertThat(entrada.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(String.valueOf(entrada.getBody().get("mensagem"))).contains("suspensa");
        assertThat(usuarioRepository.findByEmail("suspensa@teste.com").orElseThrow().getStatus())
                .isEqualTo(StatusConta.SUSPENSA);
    }

    @Test
    void redefinicaoPorEmailDerrubaAsSessoesAbertas() {
        ativa("sessao@teste.com", "senhaAntiga1");
        String tokenAntigo = token(login("sessao@teste.com", "senhaAntiga1"));
        recuperar("sessao@teste.com");
        String tokenRecuperacao = tokenRepository.findAll().get(0).getToken();

        redefinir(tokenRecuperacao, "senhaNova123");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenAntigo);
        ResponseEntity<Map> comTokenAntigo = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(comTokenAntigo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("sessao@teste.com", "senhaNova123").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void redefinicaoPorEmailLimpaATrocaObrigatoriaEAExpiracaoDaSenhaTemporaria() {
        Usuario usuario = usuarioRepository.save(Usuario.criadaPeloGestor("T", "temp@teste.com", Papel.GESTOR_TECNOLOGIA,
                passwordEncoder.encode("Temporaria123"), Instant.now(), Instant.now().plusSeconds(3600)));
        recuperar(usuario.getEmail());
        String token = tokenRepository.findAll().get(0).getToken();

        redefinir(token, "senhaNova123");

        Usuario depois = usuarioRepository.findByEmail("temp@teste.com").orElseThrow();
        assertThat(depois.isPrecisaTrocarSenha()).isFalse();
        assertThat(depois.getSenhaTemporariaExpiraEm()).isNull();
    }
}
