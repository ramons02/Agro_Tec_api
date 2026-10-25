package com.agroclima.api.business.usuario;

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

import static org.assertj.core.api.Assertions.assertThat;

/** Achados da auditoria de 2026-10-24: tamanho e tipo dos campos, e cabecalhos de seguranca da API. */
class ValidacaoDeEntradaIT extends IntegrationTestBase {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private JwtService jwtService;

    private String token;

    @BeforeEach
    void preparar() {
        Usuario u = usuarioRepository.save(new Usuario("Quem Envia", "envia@teste.com", "h", Papel.PRODUTOR_RURAL));
        token = jwtService.criarToken(u.getId(), u.getPapel()).token();
    }

    private ResponseEntity<String> enviar(String caminho, String json, String tokenUsado) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tokenUsado != null) {
            headers.setBearerAuth(tokenUsado);
        }
        return restTemplate.exchange("/api/v1" + caminho, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    @Test
    void nomeDePropriedadeAcimaDoLimiteDaColunaResponde422EmVezDe500() {
        for (int tamanho : new int[] {101, 100_000}) {
            String json = "{\"nome\":\"" + "A".repeat(tamanho) + "\",\"municipio\":\"Belém\"}";
            assertThat(enviar("/propriedades", json, token).getStatusCode())
                    .as("nome com %s caracteres", tamanho).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }
        assertThat(enviar("/propriedades", "{\"nome\":\"" + "A".repeat(100) + "\",\"municipio\":\"Belém\"}", token).getStatusCode())
                .as("100 caracteres ainda cabem").isEqualTo(HttpStatus.OK);
    }

    @Test
    void municipioAcimaDoLimiteResponde422() {
        String json = "{\"nome\":\"Fazenda\",\"municipio\":\"" + "M".repeat(101) + "\"}";
        assertThat(enviar("/propriedades", json, token).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void nomeComTipoErradoNaoViraTextoSemAviso() {
        assertThat(enviar("/propriedades", "{\"nome\":123}", token).getStatusCode().is4xxClientError()).isTrue();
        assertThat(enviar("/propriedades", "{\"nome\":true}", token).getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void talhaoComNomeAcimaDe50CaracteresResponde422() {
        String json = "{\"propriedade_id\":\"00000000-0000-0000-0000-000000000000\",\"nome\":\"" + "T".repeat(51)
                + "\",\"geometria\":{\"type\":\"Polygon\",\"coordinates\":[[[-48.5,-1.45],[-48.49,-1.45],[-48.49,-1.44],[-48.5,-1.45]]]}}";
        assertThat(enviar("/talhoes", json, token).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void cadastroPublicoRecusaCamposGigantesESenhaAcimaDoLimiteDoBcrypt() {
        String base = "\"papel\":\"PRODUTOR_RURAL\"";
        assertThat(enviar("/auth/registro", "{\"nome\":\"" + "N".repeat(300) + "\",\"email\":\"a@teste.com\"," + base + "}", null)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(enviar("/auth/registro", "{\"nome\":\"Ana\",\"email\":\"a@teste.com\",\"senha\":\"" + "s".repeat(100) + "\"," + base + "}", null)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void loginRecusaSenhaGigante() {
        assertThat(enviar("/auth/login", "{\"email\":\"a@teste.com\",\"senha\":\"" + "x".repeat(100_000) + "\"}", null)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void respostasTrazemCabecalhosDeSegurancaDaApi() {
        HttpHeaders h = restTemplate.getForEntity("/health", String.class).getHeaders();

        assertThat(h.getFirst("Content-Security-Policy")).contains("default-src 'none'").contains("frame-ancestors 'none'");
        assertThat(h.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(h.getFirst("Strict-Transport-Security")).contains("max-age=31536000").contains("includeSubDomains");
        assertThat(h.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(h.getFirst("X-Frame-Options")).isEqualTo("DENY");
    }
}
