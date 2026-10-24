package com.agroclima.api.core.security;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.grupo.Grupo;
import com.agroclima.api.business.grupo.GrupoRepository;
import com.agroclima.api.business.grupo.ParticipacaoAgronomo;
import com.agroclima.api.business.grupo.ParticipacaoAgronomoRepository;
import com.agroclima.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 024, R3 e R9: o papel e o escopo de grupo vem do banco, nao do token. Um token emitido antes de uma
 * mudanca de papel passa a agir com o papel atual, em ate 5 s (aqui, com o cache invalidado).
 */
class PapelDoBancoIT extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private GrupoRepository grupoRepository;

    @Autowired
    private ParticipacaoAgronomoRepository participacaoRepository;

    @Autowired
    private EstadoContaCache estadoContaCache;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private Usuario conta(String email, Papel papel) {
        return usuarioRepository.save(new Usuario("Conta", email, passwordEncoder.encode("senha12345"), papel));
    }

    private ResponseEntity<Map> me(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Object papelDaResposta(ResponseEntity<Map> resposta) {
        return ((Map<String, Object>) resposta.getBody().get("dados")).get("papel");
    }

    @Test
    void tokenDeGestorDeUmaContaQueOBancoJaDizAdminMasterAgeComoAdminMaster() {
        Usuario conta = conta("promovido@teste.com", Papel.GESTOR_TECNOLOGIA);
        String tokenComPapelAntigo = jwtService.criarToken(conta.getId(), Papel.GESTOR_TECNOLOGIA, 0).token();
        jdbc.update("UPDATE usuarios SET papel = 'ADMIN_MASTER'::papel_usuario WHERE id = ?", conta.getId());
        estadoContaCache.invalidar(conta.getId());

        ResponseEntity<Map> resposta = me(tokenComPapelAntigo);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(papelDaResposta(resposta)).isEqualTo("ADMIN_MASTER");
    }

    @Test
    void tokenDeContaComPapelRebaixadoNaoEscalaParaOPapelDoToken() {
        Usuario conta = conta("rebaixado@teste.com", Papel.PRODUTOR_RURAL);
        String tokenQueDizGestor = jwtService.criarToken(conta.getId(), Papel.GESTOR_TECNOLOGIA, 0).token();

        ResponseEntity<Map> resposta = me(tokenQueDizGestor);

        assertThat(papelDaResposta(resposta)).isEqualTo("PRODUTOR_RURAL");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenQueDizGestor);
        ResponseEntity<Map> lista = restTemplate.exchange("/api/v1/usuarios", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void estadoDaContaCarregaOGrupoDoProdutorEAsParticipacoesDoAgronomo() {
        Usuario gestor = conta("gestor@teste.com", Papel.GESTOR_TECNOLOGIA);
        Grupo grupoA = grupoRepository.save(new Grupo("Grupo A", gestor.getId(), "a".repeat(64), Instant.now()));
        Grupo grupoB = grupoRepository.save(new Grupo("Grupo B", gestor.getId(), "b".repeat(64), Instant.now()));
        Usuario produtor = conta("prod@teste.com", Papel.PRODUTOR_RURAL);
        produtor.definirGrupo(grupoA.getId());
        usuarioRepository.save(produtor);
        Usuario agronomo = conta("agro@teste.com", Papel.AGRONOMO);
        participacaoRepository.save(new ParticipacaoAgronomo(agronomo.getId(), grupoA.getId(), Instant.now()));
        participacaoRepository.save(new ParticipacaoAgronomo(agronomo.getId(), grupoB.getId(), Instant.now()));

        EstadoConta doProdutor = estadoContaCache.obter(produtor.getId()).orElseThrow();
        EstadoConta doAgronomo = estadoContaCache.obter(agronomo.getId()).orElseThrow();

        assertThat(doProdutor.papel()).isEqualTo(Papel.PRODUTOR_RURAL);
        assertThat(doProdutor.grupoId()).isEqualTo(grupoA.getId());
        assertThat(doProdutor.gruposDoAgronomo()).isEmpty();
        assertThat(doAgronomo.grupoId()).isNull();
        assertThat(doAgronomo.gruposDoAgronomo()).containsExactlyInAnyOrder(grupoA.getId(), grupoB.getId());
    }

    @Test
    void contaApagadaDeixaOTokenSemEstado() {
        UUID inexistente = UUID.randomUUID();

        assertThat(estadoContaCache.obter(inexistente)).isEmpty();
        assertThat(me(jwtService.criarToken(inexistente, Papel.GESTOR_TECNOLOGIA, 0).token()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
