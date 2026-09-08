package com.agroclima.api.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base para testes de integracao contra Postgres+PostGIS real. Sem Docker disponivel neste
 * ambiente (nem Docker Desktop nem o servico do sistema rodam aqui), entao NAO usa
 * Testcontainers -- conecta direto no Postgres local (mesmo usado pra dev manual), num
 * schema proprio ("java_test") separado tanto do schema "java" (dev manual) quanto do
 * "public" (tabelas do Python). Nunca aponta pro Neon de producao -- decisao explicita do
 * usuario em 2026-09-08 (Neon e a mesma base que o Python usa em producao).
 *
 * Requer DATABASE_PASSWORD no ambiente (mesma senha local usada em `mvn spring-boot:run`).
 * Se/quando Docker estiver disponivel, trocar para Testcontainers (postgis/postgis) sem
 * mudar os testes que estendem esta classe -- so a fonte da conexao muda.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://localhost:5432/agroclima_dev?currentSchema=java_test,public");
        registry.add("spring.datasource.username", () -> "ramon");
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("DATABASE_PASSWORD", ""));
        registry.add("spring.flyway.schemas", () -> "java_test");
        registry.add("spring.flyway.default-schema", () -> "java_test");
        registry.add("spring.flyway.clean-disabled", () -> "false");
    }

    /**
     * Limpa TODAS as tabelas da aplicacao antes de cada teste -- todas as classes de
     * integracao compartilham o mesmo schema fisico (java_test); uma limpeza so das
     * tabelas "da propria classe" deixa linha que quebra FK de outra classe (ja aconteceu
     * com AuthControllerIT vs ClimaControllerIT/TalhaoControllerIT). Rode antes de qualquer
     * setup proprio de subclasse (JUnit roda @BeforeEach da superclasse primeiro).
     */
    @BeforeEach
    void limparTudo() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE tokens_recuperacao_senha, vinculos_agronomo_propriedade, "
                        + "balanco_hidrico_diario, medicoes_clima, talhoes, propriedades, "
                        + "estacoes_inmet, cultura_kc, usuarios CASCADE");
    }
}
