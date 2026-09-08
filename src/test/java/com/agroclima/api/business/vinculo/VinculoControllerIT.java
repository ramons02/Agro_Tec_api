package com.agroclima.api.business.vinculo;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.core.security.JwtService;
import com.agroclima.api.support.IntegrationTestBase;
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

/** Espelha app/api/v1/endpoints/vinculos.py (feature 014). */
class VinculoControllerIT extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PropriedadeRepository propriedadeRepository;

    @Autowired
    private VinculoAgronomoPropriedadeRepository vinculoRepository;

    @Autowired
    private JwtService jwtService;

    private String criarUsuarioEToken(String email, Papel papel) {
        Usuario usuario = usuarioRepository.save(new Usuario("Usuario Vinculo", email, "hash-irrelevante", papel));
        return jwtService.criarToken(usuario.getId(), usuario.getPapel()).token();
    }

    private HttpHeaders headersCom(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void donoConvidaAgronomoExistenteComSucesso() {
        String tokenDono = criarUsuarioEToken("dono-vinc1@teste.com", Papel.PRODUTOR_RURAL);
        Usuario dono = usuarioRepository.findByEmail("dono-vinc1@teste.com").orElseThrow();
        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda Vinc1", "Belém", dono.getId(), null));
        criarUsuarioEToken("agronomo-vinc1@teste.com", Papel.AGRONOMO);

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/propriedades/" + propriedade.getId() + "/vinculos", HttpMethod.POST,
                new HttpEntity<>(Map.of("agronomo_email", "agronomo-vinc1@teste.com"), headersCom(tokenDono)), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> dados = (Map<String, Object>) resposta.getBody().get("dados");
        assertThat(dados.get("estado")).isEqualTo("CONVIDADO");
    }

    @Test
    void convidarEmailQueNaoEAgronomoRetorna422() {
        String tokenDono = criarUsuarioEToken("dono-vinc2@teste.com", Papel.PRODUTOR_RURAL);
        Usuario dono = usuarioRepository.findByEmail("dono-vinc2@teste.com").orElseThrow();
        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda Vinc2", "Belém", dono.getId(), null));
        criarUsuarioEToken("produtor-vinc2@teste.com", Papel.PRODUTOR_RURAL);

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/propriedades/" + propriedade.getId() + "/vinculos", HttpMethod.POST,
                new HttpEntity<>(Map.of("agronomo_email", "produtor-vinc2@teste.com"), headersCom(tokenDono)), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void naoDonoNaoConsegueConvidar() {
        String tokenDono = criarUsuarioEToken("dono-vinc3@teste.com", Papel.PRODUTOR_RURAL);
        Usuario dono = usuarioRepository.findByEmail("dono-vinc3@teste.com").orElseThrow();
        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda Vinc3", "Belém", dono.getId(), null));
        String tokenOutro = criarUsuarioEToken("outro-vinc3@teste.com", Papel.PRODUTOR_RURAL);
        criarUsuarioEToken("agronomo-vinc3@teste.com", Papel.AGRONOMO);

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/propriedades/" + propriedade.getId() + "/vinculos", HttpMethod.POST,
                new HttpEntity<>(Map.of("agronomo_email", "agronomo-vinc3@teste.com"), headersCom(tokenOutro)), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void agronomoConvidadoAceitaOConvite() {
        String tokenDono = criarUsuarioEToken("dono-vinc4@teste.com", Papel.PRODUTOR_RURAL);
        Usuario dono = usuarioRepository.findByEmail("dono-vinc4@teste.com").orElseThrow();
        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda Vinc4", "Belém", dono.getId(), null));
        String tokenAgronomo = criarUsuarioEToken("agronomo-vinc4@teste.com", Papel.AGRONOMO);

        ResponseEntity<Map> convite = restTemplate.exchange(
                "/api/v1/propriedades/" + propriedade.getId() + "/vinculos", HttpMethod.POST,
                new HttpEntity<>(Map.of("agronomo_email", "agronomo-vinc4@teste.com"), headersCom(tokenDono)), Map.class);
        String vinculoId = (String) ((Map<String, Object>) convite.getBody().get("dados")).get("id");

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/vinculos/" + vinculoId + "/aceitar", HttpMethod.POST,
                new HttpEntity<>(headersCom(tokenAgronomo)), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dados = (Map<String, Object>) resposta.getBody().get("dados");
        assertThat(dados.get("estado")).isEqualTo("ACEITO");
        assertThat(vinculoRepository.findById(java.util.UUID.fromString(vinculoId)).orElseThrow().getEstado())
                .isEqualTo(EstadoVinculo.ACEITO);
    }

    @Test
    void outroAgronomoNaoConsegueAceitarConviteQueNaoESeu() {
        String tokenDono = criarUsuarioEToken("dono-vinc5@teste.com", Papel.PRODUTOR_RURAL);
        Usuario dono = usuarioRepository.findByEmail("dono-vinc5@teste.com").orElseThrow();
        Propriedade propriedade = propriedadeRepository.save(new Propriedade("Fazenda Vinc5", "Belém", dono.getId(), null));
        criarUsuarioEToken("agronomo-vinc5a@teste.com", Papel.AGRONOMO);
        String tokenOutroAgronomo = criarUsuarioEToken("agronomo-vinc5b@teste.com", Papel.AGRONOMO);

        ResponseEntity<Map> convite = restTemplate.exchange(
                "/api/v1/propriedades/" + propriedade.getId() + "/vinculos", HttpMethod.POST,
                new HttpEntity<>(Map.of("agronomo_email", "agronomo-vinc5a@teste.com"), headersCom(tokenDono)), Map.class);
        String vinculoId = (String) ((Map<String, Object>) convite.getBody().get("dados")).get("id");

        ResponseEntity<Map> resposta = restTemplate.exchange(
                "/api/v1/vinculos/" + vinculoId + "/aceitar", HttpMethod.POST,
                new HttpEntity<>(headersCom(tokenOutroAgronomo)), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
