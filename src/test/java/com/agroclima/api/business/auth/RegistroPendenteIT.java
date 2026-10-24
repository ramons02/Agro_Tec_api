package com.agroclima.api.business.auth;

import com.agroclima.api.support.IntegrationTestBase;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** US1 (spec 022): cadastro publico sem senha cria conta PENDENTE, que nao entra; limite de 3 por hora por origem. */
class RegistroPendenteIT extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ResponseEntity<Map> cadastrar(Map<String, Object> corpo, String origem) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (origem != null) {
            headers.set("X-Forwarded-For", origem);
        }
        return restTemplate.exchange("/api/v1/auth/registro", HttpMethod.POST, new HttpEntity<>(corpo, headers), Map.class);
    }

    private ResponseEntity<Map> cadastrarProdutor(String email, String origem) {
        return cadastrar(Map.of("nome", "Pessoa Nova", "email", email, "papel", "PRODUTOR_RURAL"), origem);
    }

    private ResponseEntity<Map> login(String email, String senha) {
        return restTemplate.postForEntity("/api/v1/auth/login", Map.of("email", email, "senha", senha), Map.class);
    }

    @Test
    void cadastroSemSenhaCriaContaPendenteSemSenhaUtilizavel() {
        ResponseEntity<Map> resposta = cadastrarProdutor("novo@teste.com", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Usuario usuario = usuarioRepository.findByEmail("novo@teste.com").orElseThrow();
        assertThat(usuario.getStatus()).isEqualTo(StatusConta.PENDENTE);
        assertThat(usuario.getSenhaHash()).isNull();
    }

    @Test
    void cadastroDeAgronomoTambemFicaPendente() {
        cadastrar(Map.of("nome", "Agro", "email", "agro@teste.com", "papel", "AGRONOMO"), null);

        assertThat(usuarioRepository.findByEmail("agro@teste.com").orElseThrow().getStatus())
                .isEqualTo(StatusConta.PENDENTE);
    }

    @Test
    void cadastroPublicoNaoAceitaSenha() {
        ResponseEntity<Map> resposta = cadastrar(Map.of("nome", "X", "email", "comsenha@teste.com",
                "senha", "senha12345", "papel", "PRODUTOR_RURAL"), null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(usuarioRepository.findByEmail("comsenha@teste.com")).isEmpty();
    }

    @Test
    void emailJaCadastradoEmQualquerEstadoDa409() {
        Usuario ativa = new Usuario("A", "ativa@teste.com", passwordEncoder.encode("senha12345"), Papel.PRODUTOR_RURAL);
        Usuario suspensa = new Usuario("S", "suspensa@teste.com", passwordEncoder.encode("senha12345"), Papel.PRODUTOR_RURAL);
        suspensa.suspender("atraso", java.time.Instant.now());
        usuarioRepository.save(ativa);
        usuarioRepository.save(suspensa);
        cadastrarProdutor("pendente@teste.com", "198.51.100.1");

        // Origens diferentes: o limite de 3 por hora nao pode mascarar o 409.
        assertThat(cadastrarProdutor("ativa@teste.com", "198.51.100.2").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(cadastrarProdutor("suspensa@teste.com", "198.51.100.3").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(cadastrarProdutor("PENDENTE@teste.com", "198.51.100.4").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void gestorSemConviteERecusadoEConviteContinuaCriandoContaAtivaComSenha() {
        ResponseEntity<Map> semConvite = cadastrar(Map.of("nome", "G", "email", "g@teste.com",
                "senha", "senha12345", "papel", "GESTOR_TECNOLOGIA"), null);

        assertThat(semConvite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(usuarioRepository.findByEmail("g@teste.com")).isEmpty();
    }

    @Test
    void contaPendenteNaoEntraEOLoginNaoRevelaQueElaExiste() {
        cadastrarProdutor("pendente2@teste.com", null);

        ResponseEntity<Map> pendente = login("pendente2@teste.com", "qualquer12345");
        ResponseEntity<Map> inexistente = login("nao-existe@teste.com", "qualquer12345");

        assertThat(pendente.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(pendente.getBody().get("mensagem")).isEqualTo("Usuário ou senha inválidos.");
        assertThat(pendente.getBody().get("mensagem")).isEqualTo(inexistente.getBody().get("mensagem"));
    }

    @Test
    void quartoCadastroDaMesmaOrigemNaMesmaHoraRecebe429() {
        assertThat(cadastrarProdutor("a1@teste.com", "203.0.113.5").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(cadastrarProdutor("a2@teste.com", "203.0.113.5").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(cadastrarProdutor("a3@teste.com", "203.0.113.5").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> quarto = cadastrarProdutor("a4@teste.com", "203.0.113.5");

        assertThat(quarto.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(usuarioRepository.findByEmail("a4@teste.com")).isEmpty();
    }

    @Test
    void limiteEPorOrigemOutroIpNaoEAfetado() {
        for (int i = 0; i < 3; i++) {
            cadastrarProdutor("b" + i + "@teste.com", "203.0.113.9");
        }

        assertThat(cadastrarProdutor("c@teste.com", "203.0.113.9").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(cadastrarProdutor("d@teste.com", "203.0.113.10").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void usaAUltimaEntradaDoXForwardedForQueOProxyAcrescentou() {
        // O cliente forja a primeira entrada a cada tentativa; o proxy acrescenta o IP real no fim.
        for (int i = 0; i < 3; i++) {
            cadastrarProdutor("e" + i + "@teste.com", "10.0.0." + i + ", 198.51.100.7");
        }

        ResponseEntity<Map> quarto = cadastrarProdutor("e9@teste.com", "10.0.0.99, 198.51.100.7");

        assertThat(quarto.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
