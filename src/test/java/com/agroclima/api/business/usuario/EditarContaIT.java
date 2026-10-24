package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.TokenRecuperacaoSenha;
import com.agroclima.api.business.auth.TokenRecuperacaoSenhaRepository;
import com.agroclima.api.business.auth.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** US7 (spec 022): corrigir nome e e-mail; trocar o e-mail derruba as sessoes e os pedidos de recuperacao. */
class EditarContaIT extends GestaoContasITBase {

    @Autowired
    private TokenRecuperacaoSenhaRepository tokenRepository;

    private ResponseEntity<Map> editar(Usuario alvo, Map<String, Object> corpo) {
        return chamar(HttpMethod.PATCH, "/api/v1/usuarios/" + alvo.getId(), tokenGestor, corpo);
    }

    @Test
    void trocaNomeSemDerrubarASessao() {
        Usuario produtor = conta("prod@teste.com", Papel.PRODUTOR_RURAL);
        String token = login("prod@teste.com", SENHA);

        ResponseEntity<Map> resposta = editar(produtor, Map.of("nome", "Nome Corrigido"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(usuarioRepository.findById(produtor.getId()).orElseThrow().getNome()).isEqualTo("Nome Corrigido");
        assertThat(chamar(HttpMethod.GET, "/api/v1/auth/me", token, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(produtor.getId()))
                .singleElement().satisfies(a -> {
                    assertThat(a.getAcao()).isEqualTo(AcaoAuditoria.EDITAR);
                    assertThat(a.getDetalhe()).isEqualTo("alterou: nome");
                });
    }

    @Test
    void trocaDeEmailPassaAEntrarComOEnderecoNovoEDerrubaAsSessoes() {
        Usuario produtor = conta("velho@teste.com", Papel.PRODUTOR_RURAL);
        String tokenAntigo = login("velho@teste.com", SENHA);

        ResponseEntity<Map> resposta = editar(produtor, Map.of("email", "novo@teste.com"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tentarLogin("novo@teste.com", SENHA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tentarLogin("velho@teste.com", SENHA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.GET, "/api/v1/auth/me", tokenAntigo, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void trocaDeEmailApagaOsPedidosDeRecuperacaoEmAberto() {
        Usuario produtor = conta("rec@teste.com", Papel.PRODUTOR_RURAL);
        tokenRepository.save(new TokenRecuperacaoSenha(produtor.getId(), "token-em-aberto"));

        editar(produtor, Map.of("email", "rec-novo@teste.com"));

        assertThat(tokenRepository.findByToken("token-em-aberto")).isEmpty();
    }

    @Test
    void emailDeOutraContaDa409SemAlterarNada() {
        Usuario produtor = conta("a@teste.com", Papel.PRODUTOR_RURAL);
        conta("b@teste.com", Papel.PRODUTOR_RURAL);

        ResponseEntity<Map> resposta = editar(produtor, Map.of("email", "B@teste.com"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(usuarioRepository.findById(produtor.getId()).orElseThrow().getEmail()).isEqualTo("a@teste.com");
        assertThat(auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(produtor.getId())).isEmpty();
    }

    @Test
    void corpoSemNomeNemEmailDa400() {
        Usuario produtor = conta("vazio@teste.com", Papel.PRODUTOR_RURAL);

        assertThat(editar(produtor, Map.of()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void gestorCorrigeOProprioEmail() {
        ResponseEntity<Map> resposta = editar(gestor, Map.of("email", "gestor-novo@teste.com"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tentarLogin("gestor-novo@teste.com", SENHA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void produtorNaoEdita() {
        conta("prod2@teste.com", Papel.PRODUTOR_RURAL);
        Usuario alvo = conta("alvo@teste.com", Papel.PRODUTOR_RURAL);
        String tokenProdutor = login("prod2@teste.com", SENHA);

        assertThat(chamar(HttpMethod.PATCH, "/api/v1/usuarios/" + alvo.getId(), tokenProdutor, Map.of("nome", "X"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
