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

    /** Cadastro publico: sem senha (a conta nasce PENDENTE). */
    private ResponseEntity<Map> registrar(String nome, String email, String papel) {
        return restTemplate.postForEntity("/api/v1/auth/registro",
                Map.of("nome", nome, "email", email, "papel", papel), Map.class);
    }

    /** Conta ja ATIVA com senha conhecida, gravada direto (o cadastro publico agora so cria PENDENTE). */
    private void contaAtiva(String nome, String email, String senha, Papel papel) {
        usuarioRepository.save(new Usuario(nome, email, passwordEncoder.encode(senha), papel));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dados(ResponseEntity<Map> resposta) {
        return (Map<String, Object>) resposta.getBody().get("dados");
    }

    private String loginToken(String email, String senha) {
        ResponseEntity<Map> login = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "senha", senha), Map.class);
        return (String) dados(login).get("token");
    }

    private ResponseEntity<Map> criarConvite(String tokenGestor, String emailConvidado) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenGestor);
        return restTemplate.exchange("/api/v1/auth/convites-gestor", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", emailConvidado), headers), Map.class);
    }

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String tokenDeGestorExistente(String email) {
        usuarioRepository.save(new Usuario("Gestor Semente", email, passwordEncoder.encode("senha12345"),
                Papel.GESTOR_TECNOLOGIA));
        return loginToken(email, "senha12345");
    }

    @Test
    void registroPublicoDeGestorSemConviteERecusado() {
        ResponseEntity<Map> resposta = restTemplate.postForEntity("/api/v1/auth/registro",
                Map.of("nome", "Intruso", "email", "intruso@teste.com", "senha", "senha12345",
                        "papel", "GESTOR_TECNOLOGIA"), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(usuarioRepository.findByEmail("intruso@teste.com")).isEmpty();
    }

    @Test
    void produtorNaoConsegueCriarConviteDeGestor() {
        contaAtiva("Prod", "prod-conv@teste.com", "senha12345", Papel.PRODUTOR_RURAL);

        ResponseEntity<Map> resposta = criarConvite(loginToken("prod-conv@teste.com", "senha12345"), "x@teste.com");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void conviteDeGestorPermiteUmUnicoCadastroParaOEmailConvidado() {
        String tokenGestor = tokenDeGestorExistente("gestor-semente@teste.com");
        ResponseEntity<Map> convite = criarConvite(tokenGestor, "novo-gestor@teste.com");
        assertThat(convite.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String link = (String) dados(convite).get("link");
        String conviteToken = link.substring(link.indexOf("convite=") + "convite=".length());

        Map<String, Object> corpo = Map.of("nome", "Novo Gestor", "email", "novo-gestor@teste.com",
                "senha", "senha12345", "papel", "GESTOR_TECNOLOGIA", "convite", conviteToken);
        ResponseEntity<Map> outroEmail = restTemplate.postForEntity("/api/v1/auth/registro",
                Map.of("nome", "Outro", "email", "outro@teste.com", "senha", "senha12345",
                        "papel", "GESTOR_TECNOLOGIA", "convite", conviteToken), Map.class);
        ResponseEntity<Map> primeiro = restTemplate.postForEntity("/api/v1/auth/registro", corpo, Map.class);
        ResponseEntity<Map> reuso = restTemplate.postForEntity("/api/v1/auth/registro",
                Map.of("nome", "Novo Gestor", "email", "novo-gestor@teste.com", "senha", "senha12345",
                        "papel", "GESTOR_TECNOLOGIA", "convite", conviteToken), Map.class);

        assertThat(outroEmail.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(primeiro.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(dados(primeiro).get("papel")).isEqualTo("GESTOR_TECNOLOGIA");
        assertThat(reuso.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void registraUsuarioComSucesso() {
        ResponseEntity<Map> resposta = registrar("Produtor Teste", "produtor@teste.com", "PRODUTOR_RURAL");
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(dados(resposta).get("mensagem")).isEqualTo("Cadastro recebido, aguarde a confirmação.");
        Usuario criado = usuarioRepository.findByEmail("produtor@teste.com").orElseThrow();
        assertThat(criado.getStatus()).isEqualTo(StatusConta.PENDENTE);
        assertThat(criado.getSenhaHash()).isNull();
        assertThat(criado.getPapel()).isEqualTo(Papel.PRODUTOR_RURAL);
    }

    @Test
    void registraComEmailDuplicadoRetorna409() {
        registrar("Produtor Um", "duplicado@teste.com", "PRODUTOR_RURAL");
        ResponseEntity<Map> resposta = registrar("Produtor Dois", "Duplicado@Teste.com", "PRODUTOR_RURAL");
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void loginComCredenciaisValidasRetornaToken() {
        contaAtiva("Login Teste", "login@teste.com", "senha12345", Papel.PRODUTOR_RURAL);
        ResponseEntity<Map> resposta = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "login@teste.com", "senha", "senha12345"), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dados(resposta).get("token")).isNotNull();
        assertThat(dados(resposta).get("papel")).isEqualTo("PRODUTOR_RURAL");
    }

    @Test
    void loginComSenhaErradaRetorna401ComMensagemGenerica() {
        contaAtiva("Senha Errada", "senhaerrada@teste.com", "senhacerta1", Papel.PRODUTOR_RURAL);
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
        contaAtiva("Me Teste", "me@teste.com", "senha12345", Papel.PRODUTOR_RURAL);
        ResponseEntity<Map> loginResposta = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "me@teste.com", "senha", "senha12345"), Map.class);
        String token = (String) dados(loginResposta).get("token");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dados(resposta).get("papel")).isEqualTo("PRODUTOR_RURAL");
    }

    @Test
    void meSemTokenRetorna401ComEnvelopePadrao() {
        ResponseEntity<Map> resposta = restTemplate.getForEntity("/api/v1/auth/me", Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Token de autorização ausente ou expirado.");
    }

    @Test
    void recuperarSenhaRetornaMensagemIdenticaExistaOuNaoOEmail() {
        contaAtiva("Recupera Teste", "recupera@teste.com", "senha12345", Papel.PRODUTOR_RURAL);

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
        contaAtiva("Redefine Teste", "redefine@teste.com", "senhaAntiga1", Papel.PRODUTOR_RURAL);
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
        contaAtiva("Token Usado", "tokenusado@teste.com", "senhaOriginal1", Papel.PRODUTOR_RURAL);
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
