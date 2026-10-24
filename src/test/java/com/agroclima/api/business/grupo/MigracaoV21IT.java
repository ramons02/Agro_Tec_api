package com.agroclima.api.business.grupo;

import com.agroclima.api.support.IntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 024, fase 3: a V21 desliga os convites de gestor ainda nao usados e faz o convite novo valer so para Master. */
class MigracaoV21IT extends IntegrationTestBase {

    private static final String SCHEMA = "java_migracao_v21_it";

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private UUID criador;

    private Flyway flyway(String alvo) {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).defaultSchema(SCHEMA)
                .locations("classpath:db/migration").cleanDisabled(false).target(alvo).load();
    }

    @BeforeEach
    void prepararBancoNaV20() {
        jdbc = new JdbcTemplate(dataSource);
        flyway("20").clean();
        flyway("20").migrate();
        criador = UUID.randomUUID();
        jdbc.update("INSERT INTO " + SCHEMA + ".usuarios (id, nome, email, senha_hash, papel, criado_em) "
                + "VALUES (?, 'Master', 'm@v20.test', 'hash', 'ADMIN_MASTER'::" + SCHEMA + ".papel_usuario, now())", criador);
    }

    @AfterEach
    void limpar() {
        flyway("20").clean();
    }

    private void convite(String email, boolean usado) {
        jdbc.update("INSERT INTO " + SCHEMA + ".convites_gestor (id, token_hash, email, criado_por, criado_em, expira_em, usado_em) "
                        + "VALUES (?, ?, ?, ?, now(), now() + interval '5 days', " + (usado ? "now()" : "NULL") + ")",
                UUID.randomUUID(), "hash-" + email, email, criador);
    }

    private Boolean expirado(String email) {
        return jdbc.queryForObject("SELECT expira_em <= now() FROM " + SCHEMA + ".convites_gestor WHERE email = ?", Boolean.class, email);
    }

    @Test
    void conviteDeGestorAindaNaoUsadoDeixaDeValerEOUsadoFicaComoEstava() {
        convite("pendente@v20.test", false);
        convite("usado@v20.test", true);

        flyway("21").migrate();

        assertThat(expirado("pendente@v20.test")).isTrue();
        assertThat(expirado("usado@v20.test")).isFalse();
    }

    @Test
    void conviteNovoNasceParaMaster() {
        flyway("21").migrate();

        jdbc.update("INSERT INTO " + SCHEMA + ".convites_gestor (id, token_hash, email, criado_por, criado_em, expira_em) "
                + "VALUES (?, 'hash-novo', 'novo@v21.test', ?, now(), now() + interval '1 day')", UUID.randomUUID(), criador);

        assertThat(jdbc.queryForObject("SELECT papel_destino FROM " + SCHEMA + ".convites_gestor WHERE email = 'novo@v21.test'", String.class))
                .isEqualTo("ADMIN_MASTER");
    }
}
