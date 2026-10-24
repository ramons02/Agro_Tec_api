package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/** Apoio dos testes de gestao de contas: um gestor logado e chamadas autenticadas a API. */
abstract class GestaoContasITBase extends IntegrationTestBase {

    static final String SENHA = "senha12345";

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    protected AuditoriaContaRepository auditoriaRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected Usuario gestor;
    protected String tokenGestor;

    @BeforeEach
    void prepararGestor() {
        gestor = conta("gestor@teste.com", Papel.GESTOR_TECNOLOGIA);
        tokenGestor = login("gestor@teste.com", SENHA);
    }

    protected Usuario conta(String email, Papel papel) {
        return usuarioRepository.save(new Usuario("Conta " + email, email, passwordEncoder.encode(SENHA), papel));
    }

    protected ResponseEntity<Map> tentarLogin(String email, String senha) {
        return restTemplate.postForEntity("/api/v1/auth/login", Map.of("email", email, "senha", senha), Map.class);
    }

    @SuppressWarnings("unchecked")
    protected String login(String email, String senha) {
        return (String) ((Map<String, Object>) tentarLogin(email, senha).getBody().get("dados")).get("token");
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> dados(ResponseEntity<Map> resposta) {
        return (Map<String, Object>) resposta.getBody().get("dados");
    }

    protected ResponseEntity<Map> chamar(HttpMethod metodo, String caminho, String token, Object corpo) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (corpo != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(caminho, metodo, new HttpEntity<>(corpo, headers), Map.class);
    }
}
