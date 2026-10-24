package com.agroclima.api.core.response;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Erros do proprio Spring (corpo malformado, rota ou metodo inexistente, parametro invalido...) chegavam ao
 * usuario autenticado como 401 "Token ausente ou expirado", porque o despacho de erro nao passa pelo filtro
 * JWT. O front desloga o usuario em qualquer 401, entao um erro de requisicao virava logout.
 */
class ErrosDoFrameworkIT extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    private String token;

    @BeforeEach
    void preparar() {
        Usuario usuario = usuarioRepository.save(
                new Usuario("Dono Erros", "dono-erros@teste.com", "hash-irrelevante", Papel.PRODUTOR_RURAL));
        token = jwtService.criarToken(usuario.getId(), usuario.getPapel()).token();
    }

    private HttpHeaders comToken(MediaType tipo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (tipo != null) {
            headers.setContentType(tipo);
        }
        return headers;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> chamar(HttpMethod metodo, String caminho, HttpHeaders headers, Object corpo) {
        return restTemplate.exchange(caminho, metodo, new HttpEntity<>(corpo, headers), Map.class);
    }

    private void assertEnvelopeDeErro(ResponseEntity<Map> resposta, HttpStatus esperado) {
        assertThat(resposta.getStatusCode()).isEqualTo(esperado);
        assertThat(resposta.getBody()).containsEntry("status", "erro");
        assertThat(((Number) resposta.getBody().get("codigo")).intValue()).isEqualTo(esperado.value());
        assertThat(String.valueOf(resposta.getBody().get("mensagem"))).doesNotContain("Token de autorização");
    }

    @Test
    void jsonMalformadoComTokenValidoRetorna400() {
        ResponseEntity<Map> resposta = chamar(
                HttpMethod.POST, "/api/v1/propriedades", comToken(MediaType.APPLICATION_JSON), "{nao-json");

        assertEnvelopeDeErro(resposta, HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(resposta.getBody().get("mensagem"))).contains("malformado");
    }

    @Test
    void rotaInexistenteComTokenRetorna404() {
        assertEnvelopeDeErro(
                chamar(HttpMethod.GET, "/api/v1/rota-que-nao-existe", comToken(null), null), HttpStatus.NOT_FOUND);
    }

    @Test
    void metodoErradoComTokenRetorna405() {
        assertEnvelopeDeErro(
                chamar(HttpMethod.DELETE, "/api/v1/clima/atual?talhao_id=" + java.util.UUID.randomUUID(),
                        comToken(null), null),
                HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void uuidInvalidoNoCaminhoRetorna400() {
        assertEnvelopeDeErro(
                chamar(HttpMethod.GET, "/api/v1/talhoes/nao-e-um-uuid", comToken(null), null), HttpStatus.BAD_REQUEST);
    }

    @Test
    void parametroObrigatorioAusenteRetorna400() {
        assertEnvelopeDeErro(
                chamar(HttpMethod.GET, "/api/v1/clima/atual", comToken(null), null), HttpStatus.BAD_REQUEST);
    }

    @Test
    void tipoDeConteudoNaoSuportadoRetorna415() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);

        assertEnvelopeDeErro(
                chamar(HttpMethod.POST, "/api/v1/auth/login", headers, "texto-solto"), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void validacaoDeCamposContinuaRetornando422() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> resposta = chamar(HttpMethod.POST, "/api/v1/auth/registro", headers,
                Map.of("nome", "X", "email", "nao-e-email", "senha", "curta", "papel", "PRODUTOR_RURAL"));

        assertEnvelopeDeErro(resposta, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void semTokenEmRotaProtegidaContinuaRetornando401() {
        ResponseEntity<Map> resposta = chamar(HttpMethod.GET, "/api/v1/propriedades", new HttpHeaders(), null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(String.valueOf(resposta.getBody().get("mensagem"))).contains("Token de autorização");
    }

    @Test
    void rotaInexistenteSemTokenContinuaRetornando401() {
        assertThat(chamar(HttpMethod.GET, "/api/v1/rota-que-nao-existe", new HttpHeaders(), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenInvalidoContinuaRetornando401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("lixo.lixo.lixo");

        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades", headers, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
