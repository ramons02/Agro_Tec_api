package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** US8 (spec 022): historico de acoes por conta e recusa de qualquer acao por produtor e agronomo. */
class AuditoriaEPermissaoIT extends GestaoContasITBase {

    private Usuario pendente(String email) {
        return usuarioRepository.save(Usuario.pendente("Pendente", email, Papel.PRODUTOR_RURAL, Instant.now()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void detalheTrazOHistoricoDoMaisRecenteAoMaisAntigoComONomeDoAutor() {
        Usuario alvo = pendente("alvo@teste.com");
        chamar(HttpMethod.POST, "/api/v1/usuarios/" + alvo.getId() + "/confirmar", tokenGestor, null);
        chamar(HttpMethod.POST, "/api/v1/usuarios/" + alvo.getId() + "/suspender", tokenGestor, Map.of("motivo", "atraso"));
        chamar(HttpMethod.POST, "/api/v1/usuarios/" + alvo.getId() + "/reativar", tokenGestor, null);
        chamar(HttpMethod.POST, "/api/v1/usuarios/" + alvo.getId() + "/redefinir-senha", tokenGestor, null);
        chamar(HttpMethod.PATCH, "/api/v1/usuarios/" + alvo.getId(), tokenGestor, Map.of("nome", "Novo Nome"));

        ResponseEntity<Map> resposta = chamar(HttpMethod.GET, "/api/v1/usuarios/" + alvo.getId(), tokenGestor, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> historico = (List<Map<String, Object>>) dados(resposta).get("historico");
        assertThat(historico).extracting(h -> h.get("acao"))
                .containsExactly("EDITAR", "REDEFINIR_SENHA", "REATIVAR", "SUSPENDER", "CONFIRMAR");
        assertThat(historico).allSatisfy(h -> assertThat(h.get("ator_nome")).isEqualTo("Conta gestor@teste.com"));
        assertThat(historico.get(3)).containsEntry("detalhe", "atraso");
        assertThat(dados(resposta)).containsEntry("status", "ATIVA").containsEntry("email", "alvo@teste.com");
    }

    @Test
    void detalheNaoExpoeSenhaNemHash() {
        Usuario alvo = pendente("sigilo@teste.com");
        String senha = (String) dados(chamar(HttpMethod.POST,
                "/api/v1/usuarios/" + alvo.getId() + "/confirmar", tokenGestor, null)).get("senha_temporaria");

        String corpo = chamar(HttpMethod.GET, "/api/v1/usuarios/" + alvo.getId(), tokenGestor, null).getBody().toString();

        assertThat(corpo).doesNotContain(senha).doesNotContainIgnoringCase("senha_hash").doesNotContain("$2a$");
    }

    @Test
    void detalheDeContaInexistenteDa404() {
        assertThat(chamar(HttpMethod.GET, "/api/v1/usuarios/" + UUID.randomUUID(), tokenGestor, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cadaAcaoDeGestaoGeraUmRegistro() {
        Usuario confirmada = pendente("c1@teste.com");
        Usuario recusada = pendente("c2@teste.com");
        chamar(HttpMethod.POST, "/api/v1/usuarios", tokenGestor, Map.of("nome", "Criada", "email", "c3@teste.com", "papel", "AGRONOMO"));
        chamar(HttpMethod.POST, "/api/v1/usuarios/" + confirmada.getId() + "/confirmar", tokenGestor, null);
        chamar(HttpMethod.DELETE, "/api/v1/usuarios/" + recusada.getId(), tokenGestor, null);
        chamar(HttpMethod.POST, "/api/v1/usuarios/" + confirmada.getId() + "/suspender", tokenGestor, null);
        chamar(HttpMethod.POST, "/api/v1/usuarios/" + confirmada.getId() + "/reativar", tokenGestor, null);
        chamar(HttpMethod.POST, "/api/v1/usuarios/" + confirmada.getId() + "/redefinir-senha", tokenGestor, null);
        chamar(HttpMethod.PATCH, "/api/v1/usuarios/" + confirmada.getId(), tokenGestor, Map.of("nome", "Outro"));

        assertThat(auditoriaRepository.findAll()).extracting(AuditoriaConta::getAcao)
                .containsExactlyInAnyOrder(AcaoAuditoria.CRIAR, AcaoAuditoria.CONFIRMAR, AcaoAuditoria.RECUSAR,
                        AcaoAuditoria.SUSPENDER, AcaoAuditoria.REATIVAR, AcaoAuditoria.REDEFINIR_SENHA, AcaoAuditoria.EDITAR);
        assertThat(auditoriaRepository.findAll()).allSatisfy(a -> {
            assertThat(a.getAtorId()).isEqualTo(gestor.getId());
            assertThat(a.getCriadoEm()).isNotNull();
            assertThat(a.getAlvoEmail()).isNotBlank();
        });
    }

    @Test
    void produtorEAgronomoRecebem403EmTodasAsRotasDeGestao() {
        conta("prod@teste.com", Papel.PRODUTOR_RURAL);
        conta("agro@teste.com", Papel.AGRONOMO);
        Usuario alvo = pendente("alvo2@teste.com");
        String base = "/api/v1/usuarios/" + alvo.getId();

        for (String email : List.of("prod@teste.com", "agro@teste.com")) {
            String token = login(email, SENHA);
            assertThat(chamar(HttpMethod.GET, base, token, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(chamar(HttpMethod.POST, "/api/v1/usuarios", token,
                    Map.of("nome", "X", "email", "x@teste.com", "papel", "PRODUTOR_RURAL")).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(chamar(HttpMethod.POST, base + "/confirmar", token, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(chamar(HttpMethod.DELETE, base, token, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(chamar(HttpMethod.POST, base + "/suspender", token, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(chamar(HttpMethod.POST, base + "/reativar", token, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(chamar(HttpMethod.POST, base + "/redefinir-senha", token, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(chamar(HttpMethod.PATCH, base, token, Map.of("nome", "X")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void semTokenTodasAsRotasDao401() {
        String base = "/api/v1/usuarios/" + UUID.randomUUID();

        assertThat(chamar(HttpMethod.GET, base, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.POST, base + "/confirmar", null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.DELETE, base, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
