package com.agroclima.api.business.telegram;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.core.security.JwtService;
import com.agroclima.api.support.TelegramIntegrationTestBase;
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

/** Espelha contracts/telegram.md (feature 017, User Story 1). */
class TelegramControllerIT extends TelegramIntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    private String criarUsuarioEToken(String email) {
        Usuario usuario = usuarioRepository.save(new Usuario("Dono Telegram", email, "hash-irrelevante", Papel.PRODUTOR_RURAL));
        return jwtService.criarToken(usuario.getId(), usuario.getPapel()).token();
    }

    private HttpHeaders headersCom(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private String gerarLinkENextrairToken(String token) {
        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/telegram/vincular-link", HttpMethod.POST, new HttpEntity<>(headersCom(token)), Map.class);
        Map<String, Object> dados = (Map<String, Object>) resposta.getBody().get("dados");
        String link = (String) dados.get("link");
        return link.substring(link.indexOf("start=") + "start=".length());
    }

    @Test
    @SuppressWarnings("unchecked")
    void gerarLinkDeVinculoRetornaUrlComToken() {
        String token = criarUsuarioEToken("dono-tg1@teste.com");

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/telegram/vincular-link", HttpMethod.POST, new HttpEntity<>(headersCom(token)), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = (Map<String, Object>) resposta.getBody().get("dados");
        assertThat((String) dados.get("link")).startsWith("https://t.me/AgroClimaParaTesteBot?start=");
    }

    @Test
    void webhookComTokenValidoVinculaChatId() {
        String token = criarUsuarioEToken("dono-tg2@teste.com");
        Usuario usuario = usuarioRepository.findByEmail("dono-tg2@teste.com").orElseThrow();
        String tokenVinculo = gerarLinkENextrairToken(token);

        HttpHeaders headersJson = new HttpHeaders();
        headersJson.setContentType(MediaType.APPLICATION_JSON);
        String payload = "{\"message\":{\"chat\":{\"id\":123456789},\"text\":\"/start " + tokenVinculo + "\"}}";

        ResponseEntity<Map> resposta = restTemplate.postForEntity(
                "/api/v1/telegram/webhook", new HttpEntity<>(payload, headersJson), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().get("ok")).isEqualTo(true);
        assertThat(usuarioRepository.findById(usuario.getId()).orElseThrow().getTelegramChatId())
                .isEqualTo("123456789");
    }

    @Test
    void webhookComTokenInvalidoNaoVinculaNadaEAindaRespondeOk() {
        HttpHeaders headersJson = new HttpHeaders();
        headersJson.setContentType(MediaType.APPLICATION_JSON);
        String payload = "{\"message\":{\"chat\":{\"id\":999},\"text\":\"/start token-que-nao-existe\"}}";

        ResponseEntity<Map> resposta = restTemplate.postForEntity(
                "/api/v1/telegram/webhook", new HttpEntity<>(payload, headersJson), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().get("ok")).isEqualTo(true);
    }

    @Test
    void tokenDeVinculoJaUsadoNaoVinculaDeNovo() {
        String token = criarUsuarioEToken("dono-tg3@teste.com");
        String tokenVinculo = gerarLinkENextrairToken(token);
        HttpHeaders headersJson = new HttpHeaders();
        headersJson.setContentType(MediaType.APPLICATION_JSON);
        String payload = "{\"message\":{\"chat\":{\"id\":111},\"text\":\"/start " + tokenVinculo + "\"}}";
        restTemplate.postForEntity("/api/v1/telegram/webhook", new HttpEntity<>(payload, headersJson), Map.class);

        String payloadSegundaTentativa = "{\"message\":{\"chat\":{\"id\":222},\"text\":\"/start " + tokenVinculo + "\"}}";
        restTemplate.postForEntity(
                "/api/v1/telegram/webhook", new HttpEntity<>(payloadSegundaTentativa, headersJson), Map.class);

        Usuario usuario = usuarioRepository.findByEmail("dono-tg3@teste.com").orElseThrow();
        assertThat(usuario.getTelegramChatId()).isEqualTo("111");
    }

    @Test
    void desvincularLimpaChatId() {
        String token = criarUsuarioEToken("dono-tg4@teste.com");
        Usuario usuario = usuarioRepository.findByEmail("dono-tg4@teste.com").orElseThrow();
        usuario.vincularTelegram("555");
        usuarioRepository.save(usuario);

        ResponseEntity<Void> resposta = restTemplate.exchange(
                "/api/v1/telegram/vinculo", HttpMethod.DELETE, new HttpEntity<>(headersCom(token)), Void.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(usuarioRepository.findById(usuario.getId()).orElseThrow().getTelegramChatId()).isNull();
    }
}
