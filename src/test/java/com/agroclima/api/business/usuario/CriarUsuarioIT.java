package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.StatusConta;
import com.agroclima.api.business.auth.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** US5 (spec 022): o gestor cria produtor ou agronomo ja ATIVO, com senha temporaria e troca obrigatoria. */
class CriarUsuarioIT extends GestaoContasITBase {

    private ResponseEntity<Map> criar(String nome, String email, String papel) {
        return chamar(HttpMethod.POST, "/api/v1/usuarios", tokenGestor, Map.of("nome", nome, "email", email, "papel", papel));
    }

    @Test
    void criaContaAtivaComSenhaTemporariaEExpiracaoDeSeteDias() {
        ResponseEntity<Map> resposta = criar("Maria", "maria@teste.com", "AGRONOMO");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-store");
        String senha = (String) dados(resposta).get("senha_temporaria");
        assertThat(senha).hasSizeGreaterThanOrEqualTo(12);
        Usuario criada = usuarioRepository.findByEmail("maria@teste.com").orElseThrow();
        assertThat(criada.getStatus()).isEqualTo(StatusConta.ATIVA);
        assertThat(criada.getPapel()).isEqualTo(Papel.AGRONOMO);
        assertThat(criada.isPrecisaTrocarSenha()).isTrue();
        assertThat(passwordEncoder.matches(senha, criada.getSenhaHash())).isTrue();
        assertThat(criada.getSenhaTemporariaExpiraEm()).isAfter(Instant.now().plus(Duration.ofDays(7)).minusSeconds(60));
    }

    @Test
    void aContaCriadaEntraComASenhaTemporariaESoPodeTrocarASenha() {
        String senha = (String) dados(criar("Joao", "joao@teste.com", "PRODUTOR_RURAL")).get("senha_temporaria");

        ResponseEntity<Map> login = tentarLogin("joao@teste.com", senha);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dados(login)).containsEntry("precisa_trocar_senha", true);
    }

    @Test
    void emailExistenteDa409() {
        ResponseEntity<Map> resposta = criar("Outro", "GESTOR@teste.com", "PRODUTOR_RURAL");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void gestorSoEntraPorConvite() {
        ResponseEntity<Map> resposta = criar("Novo Gestor", "novogestor@teste.com", "GESTOR_TECNOLOGIA");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(usuarioRepository.findByEmail("novogestor@teste.com")).isEmpty();
    }

    @Test
    void dadosInvalidosDao422() {
        assertThat(criar("", "x@teste.com", "PRODUTOR_RURAL").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(criar("X", "nao-e-email", "PRODUTOR_RURAL").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void auditoriaCriarGravadaSemASenha() {
        String senha = (String) dados(criar("Ana", "ana@teste.com", "PRODUTOR_RURAL")).get("senha_temporaria");
        Usuario criada = usuarioRepository.findByEmail("ana@teste.com").orElseThrow();

        assertThat(auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(criada.getId())).singleElement().satisfies(a -> {
            assertThat(a.getAcao()).isEqualTo(AcaoAuditoria.CRIAR);
            assertThat(a.getAtorId()).isEqualTo(gestor.getId());
            assertThat(String.valueOf(a.getDetalhe())).doesNotContain(senha);
        });
    }

    @Test
    void produtorNaoCria() {
        conta("prod@teste.com", Papel.PRODUTOR_RURAL);
        String tokenProdutor = login("prod@teste.com", SENHA);

        ResponseEntity<Map> resposta = chamar(HttpMethod.POST, "/api/v1/usuarios", tokenProdutor,
                Map.of("nome", "X", "email", "x@teste.com", "papel", "PRODUTOR_RURAL"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
