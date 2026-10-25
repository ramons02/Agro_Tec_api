package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.grupo.Grupo;
import com.agroclima.api.business.grupo.GrupoRepository;
import com.agroclima.api.business.grupo.ParticipacaoAgronomo;
import com.agroclima.api.business.grupo.ParticipacaoAgronomoRepository;
import com.agroclima.api.core.security.JwtService;
import com.agroclima.api.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** GET /auth/me alimenta o menu do avatar: nome, e-mail, papel e grupo de quem esta logado, nunca dado sensivel. */
class PerfilDoUsuarioIT extends IntegrationTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private GrupoRepository grupoRepository;
    @Autowired private ParticipacaoAgronomoRepository participacaoRepository;
    @Autowired private JwtService jwtService;

    private JsonNode me(Usuario usuario) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtService.criarToken(usuario.getId(), usuario.getPapel(), usuario.getVersaoSessao()).token());
        ResponseEntity<String> r = restTemplate.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).doesNotContain("senha").doesNotContain("hash");
        return JSON.readTree(r.getBody()).path("dados");
    }

    @Test
    void produtorVeNomeEmailPapelEONomeDoGrupo() throws Exception {
        Usuario admin = usuarioRepository.save(new Usuario("Admin Alfa", "alfa@teste.com", "h", Papel.GESTOR_TECNOLOGIA));
        Grupo grupo = grupoRepository.save(new Grupo("Cooperativa Alfa", admin.getId(), "hash-link", Instant.now()));
        Usuario produtor = new Usuario("Paula Produtora", "paula@teste.com", "h", Papel.PRODUTOR_RURAL);
        produtor.definirGrupo(grupo.getId());
        produtor = usuarioRepository.save(produtor);

        JsonNode dados = me(produtor);

        assertThat(dados.path("nome").asText()).isEqualTo("Paula Produtora");
        assertThat(dados.path("email").asText()).isEqualTo("paula@teste.com");
        assertThat(dados.path("papel").asText()).isEqualTo("PRODUTOR_RURAL");
        assertThat(dados.path("status").asText()).isEqualTo("ATIVA");
        assertThat(dados.path("grupos")).hasSize(1);
        assertThat(dados.path("grupos").get(0).asText()).isEqualTo("Cooperativa Alfa");
        assertThat(dados.path("acesso_total").asBoolean()).isFalse();
        assertThat(dados.path("criado_em").asText()).isNotBlank();
    }

    @Test
    void agronomoListaTodosOsGruposEmQueEstaOrdenados() throws Exception {
        Usuario admin = usuarioRepository.save(new Usuario("Admin", "adm@teste.com", "h", Papel.GESTOR_TECNOLOGIA));
        Grupo b = grupoRepository.save(new Grupo("Beta", admin.getId(), "h1", Instant.now()));
        Grupo a = grupoRepository.save(new Grupo("Alfa", admin.getId(), "h2", Instant.now()));
        Usuario agronomo = usuarioRepository.save(new Usuario("Agro", "agro@teste.com", "h", Papel.AGRONOMO));
        participacaoRepository.save(new ParticipacaoAgronomo(agronomo.getId(), b.getId(), Instant.now()));
        participacaoRepository.save(new ParticipacaoAgronomo(agronomo.getId(), a.getId(), Instant.now()));

        JsonNode dados = me(agronomo);

        assertThat(dados.path("grupos")).hasSize(2);
        assertThat(dados.path("grupos").get(0).asText()).isEqualTo("Alfa");
        assertThat(dados.path("grupos").get(1).asText()).isEqualTo("Beta");
    }

    @Test
    void masterTemAcessoTotalESemGrupoVemListaVazia() throws Exception {
        Usuario master = usuarioRepository.save(new Usuario("Master", "master@teste.com", "h", Papel.ADMIN_MASTER));

        JsonNode dados = me(master);

        assertThat(dados.path("papel").asText()).isEqualTo("ADMIN_MASTER");
        assertThat(dados.path("acesso_total").asBoolean()).isTrue();
        assertThat(dados.path("grupos")).isEmpty();
    }
}
