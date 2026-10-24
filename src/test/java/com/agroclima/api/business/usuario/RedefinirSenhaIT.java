package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.StatusConta;
import com.agroclima.api.business.auth.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** US6 (spec 022): redefinir a senha gera nova temporaria e derruba a senha antiga e as sessoes abertas. */
class RedefinirSenhaIT extends GestaoContasITBase {

    private ResponseEntity<Map> redefinir(Usuario alvo) {
        return chamar(HttpMethod.POST, "/api/v1/usuarios/" + alvo.getId() + "/redefinir-senha", tokenGestor, null);
    }

    @Test
    void geraNovaSenhaTemporariaComTrocaObrigatoria() {
        Usuario produtor = conta("prod@teste.com", Papel.PRODUTOR_RURAL);

        ResponseEntity<Map> resposta = redefinir(produtor);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-store");
        String senha = (String) dados(resposta).get("senha_temporaria");
        assertThat(senha).hasSizeGreaterThanOrEqualTo(12);
        Usuario depois = usuarioRepository.findById(produtor.getId()).orElseThrow();
        assertThat(depois.isPrecisaTrocarSenha()).isTrue();
        assertThat(depois.getSenhaTemporariaExpiraEm()).isAfter(Instant.now().plusSeconds(6 * 24 * 3600));
        assertThat(dados(tentarLogin("prod@teste.com", senha))).containsEntry("precisa_trocar_senha", true);
    }

    @Test
    void senhaAntigaDeixaDeEntrar() {
        Usuario produtor = conta("antiga@teste.com", Papel.PRODUTOR_RURAL);

        redefinir(produtor);

        assertThat(tentarLogin("antiga@teste.com", SENHA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void sessaoAbertaCaiNaProximaAcao() {
        Usuario produtor = conta("logado@teste.com", Papel.PRODUTOR_RURAL);
        String tokenAntigo = login("logado@teste.com", SENHA);
        assertThat(chamar(HttpMethod.GET, "/api/v1/auth/me", tokenAntigo, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        redefinir(produtor);

        assertThat(chamar(HttpMethod.GET, "/api/v1/auth/me", tokenAntigo, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void contaSuspensaPodeTerASenhaRedefinidaEContinuaSuspensa() {
        Usuario produtor = conta("susp@teste.com", Papel.PRODUTOR_RURAL);
        produtor.suspender("atraso", Instant.now());
        usuarioRepository.save(produtor);

        assertThat(redefinir(produtor).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(usuarioRepository.findById(produtor.getId()).orElseThrow().getStatus()).isEqualTo(StatusConta.SUSPENSA);
    }

    @Test
    void contaPendenteDa409() {
        Usuario pendente = usuarioRepository.save(Usuario.pendente("P", "pend@teste.com", Papel.PRODUTOR_RURAL, Instant.now()));

        ResponseEntity<Map> resposta = redefinir(pendente);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(usuarioRepository.findById(pendente.getId()).orElseThrow().getSenhaHash()).isNull();
    }

    @Test
    void auditoriaRedefinirSenhaGravada() {
        Usuario produtor = conta("audit@teste.com", Papel.PRODUTOR_RURAL);
        String senha = (String) dados(redefinir(produtor)).get("senha_temporaria");

        assertThat(auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(produtor.getId())).singleElement().satisfies(a -> {
            assertThat(a.getAcao()).isEqualTo(AcaoAuditoria.REDEFINIR_SENHA);
            assertThat(a.getAtorId()).isEqualTo(gestor.getId());
            assertThat(String.valueOf(a.getDetalhe())).doesNotContain(senha);
        });
    }

    @Test
    void produtorNaoRedefine() {
        conta("prod2@teste.com", Papel.PRODUTOR_RURAL);
        Usuario alvo = conta("alvo@teste.com", Papel.PRODUTOR_RURAL);
        String tokenProdutor = login("prod2@teste.com", SENHA);

        assertThat(chamar(HttpMethod.POST, "/api/v1/usuarios/" + alvo.getId() + "/redefinir-senha", tokenProdutor, null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
