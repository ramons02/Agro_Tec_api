package com.agroclima.api.business.vinculo;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.grupo.Grupo;
import com.agroclima.api.business.grupo.GrupoRepository;
import com.agroclima.api.business.grupo.ParticipacaoAgronomo;
import com.agroclima.api.business.grupo.ParticipacaoAgronomoRepository;
import com.agroclima.api.support.GruposDeTeste;
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

    @Autowired
    private GrupoRepository grupoRepository;

    @Autowired
    private ParticipacaoAgronomoRepository participacaoRepository;

    private Grupo grupo;

    @org.junit.jupiter.api.BeforeEach
    void criarGrupoDosTestes() {
        Usuario admin = usuarioRepository.save(new Usuario("Admin", "admin-vinc@teste.com", "hash", Papel.GESTOR_TECNOLOGIA));
        grupo = GruposDeTeste.criarGrupo(grupoRepository, usuarioRepository, "Grupo Vinculos", admin);
    }

    private String criarUsuarioEToken(String email, Papel papel) {
        Usuario novo = new Usuario("Usuario Vinculo", email, "hash-irrelevante", papel);
        if (papel != Papel.AGRONOMO) {
            novo.definirGrupo(grupo.getId());
        }
        Usuario usuario = usuarioRepository.save(novo);
        if (papel == Papel.AGRONOMO) {
            participacaoRepository.save(new ParticipacaoAgronomo(usuario.getId(), grupo.getId(), java.time.Instant.now()));
        }
        return jwtService.criarToken(usuario.getId(), usuario.getPapel()).token();
    }

    private Propriedade propriedadeDoGrupo(String nome, Usuario dono) {
        Propriedade propriedade = new Propriedade(nome, "Belém", dono.getId(), null);
        propriedade.definirGrupo(dono.getGrupoId());
        return propriedadeRepository.save(propriedade);
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
        Propriedade propriedade = propriedadeDoGrupo("Fazenda Vinc1", dono);
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
        Propriedade propriedade = propriedadeDoGrupo("Fazenda Vinc2", dono);
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
        Propriedade propriedade = propriedadeDoGrupo("Fazenda Vinc3", dono);
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
        Propriedade propriedade = propriedadeDoGrupo("Fazenda Vinc4", dono);
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
        Propriedade propriedade = propriedadeDoGrupo("Fazenda Vinc5", dono);
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
