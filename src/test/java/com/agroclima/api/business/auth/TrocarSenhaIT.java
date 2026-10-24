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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** US3 (spec 022): primeiro acesso com senha temporaria e troca da propria senha. */
class TrocarSenhaIT extends IntegrationTestBase {

    private static final String TEMPORARIA = "Temporaria2026";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario contaComSenhaTemporaria(String email, Instant expiraEm) {
        return usuarioRepository.save(Usuario.criadaPeloGestor("Pessoa", email, Papel.PRODUTOR_RURAL,
                passwordEncoder.encode(TEMPORARIA), Instant.now(), expiraEm));
    }

    private Usuario contaComSenhaTemporaria(String email) {
        return contaComSenhaTemporaria(email, Instant.now().plus(Duration.ofDays(7)));
    }

    private ResponseEntity<Map> login(String email, String senha) {
        return restTemplate.postForEntity("/api/v1/auth/login", Map.of("email", email, "senha", senha), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dados(ResponseEntity<Map> resposta) {
        return (Map<String, Object>) resposta.getBody().get("dados");
    }

    private ResponseEntity<Map> chamar(HttpMethod metodo, String caminho, String token, Object corpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(caminho, metodo, new HttpEntity<>(corpo, headers), Map.class);
    }

    private ResponseEntity<Map> trocar(String token, String atual, String nova) {
        return chamar(HttpMethod.POST, "/api/v1/auth/trocar-senha", token, Map.of("senha_atual", atual, "senha_nova", nova));
    }

    @Test
    void loginComSenhaTemporariaDevolveTokenRestritoESinalizaATroca() {
        contaComSenhaTemporaria("primeiro@teste.com");

        ResponseEntity<Map> resposta = login("primeiro@teste.com", TEMPORARIA);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dados(resposta)).containsEntry("precisa_trocar_senha", true);
    }

    @Test
    void tokenRestritoSoAlcancaATrocaDeSenha() {
        contaComSenhaTemporaria("restrito@teste.com");
        String token = (String) dados(login("restrito@teste.com", TEMPORARIA)).get("token");

        ResponseEntity<Map> me = chamar(HttpMethod.GET, "/api/v1/auth/me", token, null);
        ResponseEntity<Map> propriedades = chamar(HttpMethod.GET, "/api/v1/propriedades", token, null);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(propriedades.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(String.valueOf(me.getBody().get("mensagem"))).contains("Troca de senha obrigatória");
    }

    @Test
    void senhaNovaCurtaOuIgualATemporariaERecusada() {
        contaComSenhaTemporaria("regras@teste.com");
        String token = (String) dados(login("regras@teste.com", TEMPORARIA)).get("token");

        ResponseEntity<Map> curta = trocar(token, TEMPORARIA, "curta");
        ResponseEntity<Map> igual = trocar(token, TEMPORARIA, TEMPORARIA);

        assertThat(curta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(igual.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(usuarioRepository.findByEmail("regras@teste.com").orElseThrow().isPrecisaTrocarSenha()).isTrue();
    }

    @Test
    void senhaAtualErradaERecusadaSemMudarNada() {
        contaComSenhaTemporaria("errada@teste.com");
        String token = (String) dados(login("errada@teste.com", TEMPORARIA)).get("token");
        String hashAntes = usuarioRepository.findByEmail("errada@teste.com").orElseThrow().getSenhaHash();

        ResponseEntity<Map> resposta = trocar(token, "SenhaErrada123", "NovaSenha2026");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Senha atual incorreta.");
        assertThat(usuarioRepository.findByEmail("errada@teste.com").orElseThrow().getSenhaHash()).isEqualTo(hashAntes);
    }

    @Test
    void trocaComSucessoLiberaOAcessoEInvalidaATemporariaEOTokenRestrito() {
        contaComSenhaTemporaria("sucesso@teste.com");
        String tokenRestrito = (String) dados(login("sucesso@teste.com", TEMPORARIA)).get("token");

        ResponseEntity<Map> resposta = trocar(tokenRestrito, TEMPORARIA, "NovaSenha2026");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        String tokenCompleto = (String) dados(resposta).get("token");
        assertThat(chamar(HttpMethod.GET, "/api/v1/auth/me", tokenCompleto, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        // O token restrito, emitido na versao anterior, deixa de valer.
        assertThat(chamar(HttpMethod.GET, "/api/v1/auth/me", tokenRestrito, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("sucesso@teste.com", TEMPORARIA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<Map> novoLogin = login("sucesso@teste.com", "NovaSenha2026");
        assertThat(novoLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dados(novoLogin)).containsEntry("precisa_trocar_senha", false);
        Usuario depois = usuarioRepository.findByEmail("sucesso@teste.com").orElseThrow();
        assertThat(depois.isPrecisaTrocarSenha()).isFalse();
        assertThat(depois.getSenhaTemporariaExpiraEm()).isNull();
    }

    @Test
    void senhaTemporariaComMaisDeSeteDiasSoRevelaAExpiracaoComASenhaCorreta() {
        contaComSenhaTemporaria("expirada@teste.com", Instant.now().minusSeconds(1));

        ResponseEntity<Map> correta = login("expirada@teste.com", TEMPORARIA);
        ResponseEntity<Map> errada = login("expirada@teste.com", "OutraSenha123");

        assertThat(correta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(String.valueOf(correta.getBody().get("mensagem"))).contains("expirada");
        assertThat(errada.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errada.getBody().get("mensagem")).isEqualTo("Usuário ou senha inválidos.");
    }

    @Test
    void gestorTrocaAPropriaSenhaInformandoAAtual() {
        usuarioRepository.save(new Usuario("Gestor", "gestor@teste.com", passwordEncoder.encode("SenhaAtual123"), Papel.GESTOR_TECNOLOGIA));
        String token = (String) dados(login("gestor@teste.com", "SenhaAtual123")).get("token");

        ResponseEntity<Map> resposta = trocar(token, "SenhaAtual123", "SenhaNova2026");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login("gestor@teste.com", "SenhaNova2026").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login("gestor@teste.com", "SenhaAtual123").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void produtorEAgronomoNaoTrocamASenhaPorConta_SoOGestorRedefine() {
        usuarioRepository.save(new Usuario("Prod", "prod@teste.com", passwordEncoder.encode("SenhaAtual123"), Papel.PRODUTOR_RURAL));
        usuarioRepository.save(new Usuario("Agro", "agro@teste.com", passwordEncoder.encode("SenhaAtual123"), Papel.AGRONOMO));

        for (String email : new String[] {"prod@teste.com", "agro@teste.com"}) {
            String token = (String) dados(login(email, "SenhaAtual123")).get("token");

            ResponseEntity<Map> resposta = trocar(token, "SenhaAtual123", "SenhaNova2026");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(login(email, "SenhaAtual123").getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void agronomoComSenhaTemporariaDefineASenhaNoPrimeiroAcesso() {
        usuarioRepository.save(Usuario.criadaPeloGestor("Agro", "agro-novo@teste.com", Papel.AGRONOMO,
                passwordEncoder.encode(TEMPORARIA), Instant.now(), Instant.now().plus(Duration.ofDays(7))));
        String token = (String) dados(login("agro-novo@teste.com", TEMPORARIA)).get("token");

        ResponseEntity<Map> resposta = trocar(token, TEMPORARIA, "SenhaNova2026");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login("agro-novo@teste.com", "SenhaNova2026").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void trocarSenhaSemTokenDa401() {
        ResponseEntity<Map> resposta = restTemplate.postForEntity("/api/v1/auth/trocar-senha",
                Map.of("senha_atual", "x", "senha_nova", "NovaSenha2026"), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void erroRepetidoDaSenhaAtualBloqueiaPorAlgunsMinutos() {
        contaComSenhaTemporaria("forca@teste.com");
        String token = (String) dados(login("forca@teste.com", TEMPORARIA)).get("token");

        for (int i = 0; i < 5; i++) {
            assertThat(trocar(token, "Chute" + i + "abcdef", "NovaSenha2026").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        assertThat(trocar(token, TEMPORARIA, "NovaSenha2026").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
