package com.agroclima.api.core.security;

import com.agroclima.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Limites de tentativas no banco (auditoria de 2026-10-24): sobrevivem a reinicio, contam de forma atomica e nao guardam e-mail em texto. */
class LimitesPersistentesIT extends IntegrationTestBase {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ArmazemJdbc armazem;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager gerenteDeTransacao;

    private int login(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"email\":\"" + email + "\",\"senha\":\"senha-errada1\"}", headers), String.class)
                .getStatusCode().value();
    }

    @Test
    void sextaTentativaDeLoginViraBloqueioMesmoComALoginFalhoDesfazendoATransacao() {
        for (int i = 0; i < 5; i++) {
            assertThat(login("limite@teste.com")).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        assertThat(login("limite@teste.com")).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(jdbc.queryForObject("SELECT contagem FROM limite_tentativas WHERE escopo = 'login'", Integer.class))
                .isEqualTo(5);
    }

    @Test
    void ContagemFicaNoBancoEUmaInstanciaNovaEnxergaOBloqueio() {
        for (int i = 0; i < 5; i++) {
            login("reinicio@teste.com");
        }

        // Simula o reinicio da API: nenhum estado em memoria, so o que esta no banco.
        ArmazemJdbc aposReinicio = new ArmazemJdbc(jdbc, gerenteDeTransacao);
        LimitadorTentativas limitador = new LimitadorTentativas("login", 5, Duration.ofMinutes(15), aposReinicio);

        assertThat(limitador.bloqueado("reinicio@teste.com")).isTrue();
        assertThat(limitador.bloqueado("outro@teste.com")).isFalse();
    }

    @Test
    void ChaveNaoFicaEmTextoSoOHashSha256() {
        login("sigilo@teste.com");

        String chave = jdbc.queryForObject("SELECT chave_hash FROM limite_tentativas", String.class);

        assertThat(chave).hasSize(64).doesNotContain("sigilo").doesNotContain("@");
        assertThat(chave).isEqualTo(ArmazemJdbc.hash("SIGILO@teste.com "));
    }

    @Test
    void JanelaVencidaRecomecaDoUmEOJobApagaAsLinhasVencidas() {
        for (int i = 0; i < 5; i++) {
            armazem.incrementar("login", "vence@teste.com", Duration.ofMinutes(15));
        }
        jdbc.update("UPDATE limite_tentativas SET expira_em = now() - interval '1 minute'");

        assertThat(armazem.atual("login", "vence@teste.com")).isZero();
        assertThat(armazem.removerVencidos()).isEqualTo(1);
        assertThat(armazem.incrementar("login", "vence@teste.com", Duration.ofMinutes(15))).isEqualTo(1);
    }

    @Test
    void LinhaVencidaQueRecebeNovaTentativaRecomecaEmUmSemApagarAntes() {
        for (int i = 0; i < 4; i++) {
            armazem.incrementar("recuperacao", "x@teste.com", Duration.ofHours(1));
        }
        jdbc.update("UPDATE limite_tentativas SET expira_em = now() - interval '1 second'");

        assertThat(armazem.incrementar("recuperacao", "x@teste.com", Duration.ofHours(1))).isEqualTo(1);
    }

    @Test
    void IncrementoEAtomicoSobConcorrencia() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> tarefas = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                tarefas.add(() -> armazem.incrementar("login", "corrida@teste.com", Duration.ofMinutes(15)));
            }
            for (Future<Integer> f : pool.invokeAll(tarefas)) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }

        assertThat(armazem.atual("login", "corrida@teste.com")).isEqualTo(40);
    }

    @Test
    void EscoposSaoIndependentes() {
        armazem.incrementar("login", "mesma@teste.com", Duration.ofMinutes(15));
        armazem.incrementar("login", "mesma@teste.com", Duration.ofMinutes(15));
        armazem.incrementar("recuperacao", "mesma@teste.com", Duration.ofHours(1));

        assertThat(armazem.atual("login", "mesma@teste.com")).isEqualTo(2);
        assertThat(armazem.atual("recuperacao", "mesma@teste.com")).isEqualTo(1);
        armazem.removerEscopo("login");
        assertThat(armazem.atual("login", "mesma@teste.com")).isZero();
        assertThat(armazem.atual("recuperacao", "mesma@teste.com")).isEqualTo(1);
    }
}
