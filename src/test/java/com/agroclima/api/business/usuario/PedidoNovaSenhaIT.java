package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.StatusConta;
import com.agroclima.api.business.auth.TokenRecuperacaoSenhaRepository;
import com.agroclima.api.business.auth.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluxo de quem esqueceu a senha (spec 022): produtor e agronomo pedem na tela de login, o pedido vira uma
 * pendencia na tela do gestor, que gera a senha temporaria; a pessoa entra com ela e define a senha nova.
 */
class PedidoNovaSenhaIT extends GestaoContasITBase {

    @Autowired
    private TokenRecuperacaoSenhaRepository tokenRepository;

    private ResponseEntity<Map> pedir(String email) {
        return restTemplate.postForEntity("/api/v1/auth/recuperar-senha", Map.of("email", email), Map.class);
    }

    @Test
    void produtorQueEsqueceuASenhaGeraPendenciaParaOGestorEnaoRecebeLinkNemToken() {
        Usuario produtor = conta("esqueceu@teste.com", Papel.PRODUTOR_RURAL);

        ResponseEntity<Map> resposta = pedir("esqueceu@teste.com");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(usuarioRepository.findById(produtor.getId()).orElseThrow().getSenhaEsquecidaEm()).isNotNull();
        assertThat(tokenRepository.count()).isZero();
        assertThat(auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(produtor.getId()))
                .extracting(AuditoriaConta::getAcao).containsExactly(AcaoAuditoria.SENHA_ESQUECIDA);
    }

    @Test
    void agronomoTambemGeraPendencia() {
        Usuario agronomo = conta("agro@teste.com", Papel.AGRONOMO);

        pedir("agro@teste.com");

        assertThat(usuarioRepository.findById(agronomo.getId()).orElseThrow().getSenhaEsquecidaEm()).isNotNull();
    }

    @Test
    void respostaEIgualParaEmailInexistenteSemEfeitoNenhum() {
        conta("existe@teste.com", Papel.PRODUTOR_RURAL);

        ResponseEntity<Map> existente = pedir("existe@teste.com");
        ResponseEntity<Map> inexistente = pedir("nao-existe@teste.com");

        assertThat(existente.getBody().get("mensagem")).isEqualTo(inexistente.getBody().get("mensagem"));
        assertThat(usuarioRepository.findByEmail("nao-existe@teste.com")).isEmpty();
    }

    @Test
    void pedidoRepetidoMantemADataEoHistoricoDoPrimeiro() {
        Usuario produtor = conta("repete@teste.com", Papel.PRODUTOR_RURAL);
        pedir("repete@teste.com");
        Instant primeiro = usuarioRepository.findById(produtor.getId()).orElseThrow().getSenhaEsquecidaEm();

        pedir("repete@teste.com");

        assertThat(usuarioRepository.findById(produtor.getId()).orElseThrow().getSenhaEsquecidaEm()).isEqualTo(primeiro);
        assertThat(auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(produtor.getId())).hasSize(1);
    }

    @Test
    void contaPendenteOuSuspensaNaoGeraPendencia() {
        Usuario pendente = usuarioRepository.save(Usuario.pendente("P", "pend@teste.com", Papel.PRODUTOR_RURAL, Instant.now()));
        Usuario suspensa = conta("susp@teste.com", Papel.PRODUTOR_RURAL);
        suspensa.suspender("atraso", Instant.now());
        usuarioRepository.save(suspensa);

        pedir("pend@teste.com");
        pedir("susp@teste.com");

        assertThat(usuarioRepository.findById(pendente.getId()).orElseThrow().getSenhaEsquecidaEm()).isNull();
        assertThat(usuarioRepository.findById(suspensa.getId()).orElseThrow().getSenhaEsquecidaEm()).isNull();
    }

    @Test
    void gestorQueEsqueceuAindaRecebeOLinkPorEmail() {
        pedir("gestor@teste.com");

        assertThat(tokenRepository.count()).isEqualTo(1);
        assertThat(usuarioRepository.findById(gestor.getId()).orElseThrow().getSenhaEsquecidaEm()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void gestorVeAPendenciaNaListaNoFiltroENoResumo() {
        Usuario produtor = conta("lista@teste.com", Papel.PRODUTOR_RURAL);
        conta("quieto@teste.com", Papel.PRODUTOR_RURAL);
        pedir("lista@teste.com");

        Map<String, Object> resumo = dados(chamar(HttpMethod.GET, "/api/v1/usuarios/resumo", tokenGestor, null));
        Map<String, Object> filtrada = dados(chamar(HttpMethod.GET, "/api/v1/usuarios?esqueceu_senha=true", tokenGestor, null));

        assertThat(((Number) resumo.get("senhas_esquecidas")).longValue()).isEqualTo(1);
        List<Map<String, Object>> itens = (List<Map<String, Object>>) filtrada.get("itens");
        assertThat(itens).singleElement().satisfies(i -> {
            assertThat(i).containsEntry("id", produtor.getId().toString());
            assertThat(i.get("senha_esquecida_em")).isNotNull();
        });
    }

    @Test
    void fluxoCompletoGestorRedefineAPessoaEntraComATemporariaEDefineASenhaNova() {
        Usuario produtor = conta("fluxo@teste.com", Papel.PRODUTOR_RURAL);
        pedir("fluxo@teste.com");

        ResponseEntity<Map> redefinida = chamar(HttpMethod.POST,
                "/api/v1/usuarios/" + produtor.getId() + "/redefinir-senha", tokenGestor, null);
        String temporaria = (String) dados(redefinida).get("senha_temporaria");
        assertThat(usuarioRepository.findById(produtor.getId()).orElseThrow().getSenhaEsquecidaEm())
                .as("a pendencia some quando o gestor redefine").isNull();

        ResponseEntity<Map> entrada = tentarLogin("fluxo@teste.com", temporaria);
        assertThat(dados(entrada)).containsEntry("precisa_trocar_senha", true);
        String tokenRestrito = (String) dados(entrada).get("token");
        ResponseEntity<Map> troca = chamar(HttpMethod.POST, "/api/v1/auth/trocar-senha", tokenRestrito,
                Map.of("senha_atual", temporaria, "senha_nova", "MinhaSenha2026"));

        assertThat(troca.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tentarLogin("fluxo@teste.com", "MinhaSenha2026").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(usuarioRepository.findById(produtor.getId()).orElseThrow().getStatus()).isEqualTo(StatusConta.ATIVA);
    }

    @Test
    void quemLembraDaSenhaEEntraTiraOPedidoDaTelaDoGestor() {
        Usuario produtor = conta("lembrou@teste.com", Papel.PRODUTOR_RURAL);
        pedir("lembrou@teste.com");

        assertThat(tentarLogin("lembrou@teste.com", SENHA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(usuarioRepository.findById(produtor.getId()).orElseThrow().getSenhaEsquecidaEm()).isNull();
    }
}
