package com.agroclima.api.business.auth;

import com.agroclima.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FR-021 / SC-007: contas anteriores a V16 viram ATIVA, sem troca obrigatoria, e continuam entrando. */
class MigracaoContasIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UsuarioRepository usuarioRepository;

    /** Insere como o codigo anterior a V16 inseria: sem nenhuma coluna nova. */
    private void inserirContaDoFormatoAntigo(String email, String senha) {
        jdbc.update("INSERT INTO usuarios (id, nome, email, senha_hash, papel, criado_em) "
                        + "VALUES (?, 'Conta Antiga', ?, ?, 'PRODUTOR_RURAL'::papel_usuario, now())",
                UUID.randomUUID(), email, passwordEncoder.encode(senha));
    }

    @Test
    void contaDoFormatoAntigoFicaAtivaSemTrocaObrigatoria() {
        inserirContaDoFormatoAntigo("antiga@teste.com", "senha12345");

        Usuario usuario = usuarioRepository.findByEmail("antiga@teste.com").orElseThrow();

        assertThat(usuario.getStatus()).isEqualTo(StatusConta.ATIVA);
        assertThat(usuario.isPrecisaTrocarSenha()).isFalse();
        assertThat(usuario.getVersaoSessao()).isZero();
        assertThat(usuario.getSenhaTemporariaExpiraEm()).isNull();
    }

    @Test
    void contaDoFormatoAntigoContinuaEntrando() {
        inserirContaDoFormatoAntigo("antiga2@teste.com", "senha12345");

        ResponseEntity<Map> login = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "antiga2@teste.com", "senha", "senha12345"), Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void bancoNaoAceitaContaPendenteComSenhaNemAtivaSemSenha() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO usuarios (id, email, senha_hash, papel, criado_em, status) "
                + "VALUES (?, 'p@teste.com', 'hash', 'PRODUTOR_RURAL'::papel_usuario, now(), 'PENDENTE')",
                UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO usuarios (id, email, papel, criado_em, status) "
                + "VALUES (?, 'a@teste.com', 'PRODUTOR_RURAL'::papel_usuario, now(), 'ATIVA')",
                UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoNaoAceitaStatusInvalido() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO usuarios (id, email, senha_hash, papel, criado_em, status) "
                + "VALUES (?, 'x@teste.com', 'hash', 'PRODUTOR_RURAL'::papel_usuario, now(), 'BANIDA')",
                UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }
}
