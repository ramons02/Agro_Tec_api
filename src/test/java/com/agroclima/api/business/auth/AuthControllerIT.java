package com.agroclima.api.business.auth;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Espelha tests/contract/test_auth_login.py e test_auth_registro_recuperacao.py. */
class AuthControllerIT extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TokenRecuperacaoSenhaRepository tokenRepository;

    @BeforeEach
    void limpar() {
        tokenRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    private ResponseEntity<Map> registrar(String nome, String email, String senha, String papel) {
        return restTemplate.postForEntity("/api/v1/auth/registro",
                Map.of("nome", nome, "email", email, "senha", senha, "papel", papel), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dados(ResponseEntity<Map> resposta) {
        return (Map<String, Object>) resposta.getBody().get("dados");
    }

    @Test
    void registraUsuarioComSucesso() {
        ResponseEntity<Map> resposta = registrar("Produtor Teste", "produtor@teste.com", "senha12345", "PRODUTOR_RURAL");
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(dados(resposta).get("email")).isEqualTo("produtor@teste.com");
        assertThat(dados(resposta).get("papel")).isEqualTo("PRODUTOR_RURAL");
    }

    @Test
    void registraComEmailDuplicadoRetorna409() {
        registrar("Produtor Um", "duplicado@teste.com", "senha12345", "PRODUTOR_RURAL");
        ResponseEntity<Map> resposta = registrar("Produtor Dois", "duplicado@teste.com", "outrasenha1", "PRODUTOR_RURAL");
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void loginComCredenciaisValidasRetornaToken() {
        registrar("Login Teste", "login@teste.com", "senha12345", "PRODUTOR_RURAL");
        ResponseEntity<Map> resposta = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "login@teste.com", "senha", "senha12345"), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dados(resposta).get("token")).isNotNull();
        assertThat(dados(resposta).get("papel")).isEqualTo("PRODUTOR_RURAL");
    }

    @Test
    void loginComSenhaErradaRetorna401ComMensagemGenerica() {
        registrar("Senha Errada", "senhaerrada@teste.com", "senhacerta1", "PRODUTOR_RURAL");
        ResponseEntity<Map> resposta = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "senhaerrada@teste.com", "senha", "senhaerrada1"), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Usuário ou senha inválidos.");
    }

    @Test
    void loginComEmailInexistenteRetorna401ComMesmaMensagem() {
        ResponseEntity<Map> resposta = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "naoexiste@teste.com", "senha", "qualquer123"), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Usuário ou senha inválidos.");
    }

    @Test
    void meComTokenValidoRetornaUsuario() {
        registrar("Me Teste", "me@teste.com", "senha12345", "GESTOR_TECNOLOGIA");
        ResponseEntity<Map> loginResposta = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "me@teste.com", "senha", "senha12345"), Map.class);
        String token = (String) dados(loginResposta).get("token");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dados(resposta).get("papel")).isEqualTo("GESTOR_TECNOLOGIA");
    }

    @Test
    void meSemTokenRetorna401ComEnvelopePadrao() {
        ResponseEntity<Map> resposta = restTemplate.getForEntity("/api/v1/auth/me", Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Token de autorização ausente ou expirado.");
    }

    @Test
    void recuperarSenhaRetornaMensagemIdenticaExistaOuNaoOEmail() {
        registrar("Recupera Teste", "recupera@teste.com", "senha12345", "PRODUTOR_RURAL");

        ResponseEntity<Map> comEmail = restTemplate.postForEntity(
                "/api/v1/auth/recuperar-senha", Map.of("email", "recupera@teste.com"), Map.class);
        ResponseEntity<Map> semEmail = restTemplate.postForEntity(
                "/api/v1/auth/recuperar-senha", Map.of("email", "naoexiste@teste.com"), Map.class);

        assertThat(comEmail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(semEmail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(comEmail.getBody().get("mensagem")).isEqualTo(semEmail.getBody().get("mensagem"));
        assertThat(tokenRepository.count()).isEqualTo(1);
    }

    @Test
    void redefinirSenhaComTokenValidoTrocaSenha() {
        registrar("Redefine Teste", "redefine@teste.com", "senhaAntiga1", "PRODUTOR_RURAL");
        restTemplate.postForEntity("/api/v1/auth/recuperar-senha", Map.of("email", "redefine@teste.com"), Map.class);
        String token = tokenRepository.findAll().get(0).getToken();

        ResponseEntity<Map> resposta = restTemplate.postForEntity("/api/v1/auth/redefinir-senha",
                Map.of("token", token, "nova_senha", "senhaNova123"), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> loginComSenhaNova = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "redefine@teste.com", "senha", "senhaNova123"), Map.class);
        assertThat(loginComSenhaNova.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> loginComSenhaAntiga = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "redefine@teste.com", "senha", "senhaAntiga1"), Map.class);
        assertThat(loginComSenhaAntiga.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void redefinirSenhaComTokenJaUsadoFalha() {
        registrar("Token Usado", "tokenusado@teste.com", "senhaOriginal1", "PRODUTOR_RURAL");
        restTemplate.postForEntity("/api/v1/auth/recuperar-senha", Map.of("email", "tokenusado@teste.com"), Map.class);
        String token = tokenRepository.findAll().get(0).getToken();

        restTemplate.postForEntity("/api/v1/auth/redefinir-senha",
                Map.of("token", token, "nova_senha", "primeiraTroca1"), Map.class);
        ResponseEntity<Map> segundaTentativa = restTemplate.postForEntity("/api/v1/auth/redefinir-senha",
                Map.of("token", token, "nova_senha", "segundaTroca12"), Map.class);

        assertThat(segundaTentativa.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void redefinirSenhaComTokenInexistenteRetorna400() {
        ResponseEntity<Map> resposta = restTemplate.postForEntity("/api/v1/auth/redefinir-senha",
                Map.of("token", "token-que-nao-existe", "nova_senha", "qualquerSenha1"), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
