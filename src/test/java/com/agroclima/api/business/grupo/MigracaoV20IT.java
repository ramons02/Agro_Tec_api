package com.agroclima.api.business.grupo;

import com.agroclima.api.support.IntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 024, fase 2 (US7, FR-019): a V20 promove o gestor que gere o grupo inicial a Master e completa o grupo de
 * quem foi criado depois da V19. Roda sobre um banco que ja esta na V19 (producao depois da fase 1).
 */
class MigracaoV20IT extends IntegrationTestBase {

    private static final String SCHEMA = "java_migracao_v20_it";

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    private UUID gestorId;
    private UUID gestorMaisNovoId;
    private UUID produtorAntesId;
    private UUID produtorDepoisId;
    private UUID agronomoDepoisId;
    private UUID propriedadeDepoisId;
    private UUID pendenteId;

    private Flyway flyway(String alvo) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .target(alvo)
                .load();
    }

    @BeforeEach
    void prepararBancoNaV19() {
        jdbc = new JdbcTemplate(dataSource);
        flyway("19").clean();
        flyway("17").migrate();
        gestorId = usuario("gestor@v19.test", "GESTOR_TECNOLOGIA", "ATIVA", "2026-01-01T10:00:00Z");
        gestorMaisNovoId = usuario("gestor2@v19.test", "GESTOR_TECNOLOGIA", "ATIVA", "2026-06-01T10:00:00Z");
        produtorAntesId = usuario("antes@v19.test", "PRODUTOR_RURAL", "ATIVA", "2026-02-01T10:00:00Z");
        flyway("19").migrate();
        // Criados depois da V19, como numa producao entre a fase 1 e a fase 2: sem grupo.
        produtorDepoisId = usuario("depois@v19.test", "PRODUTOR_RURAL", "ATIVA", "2026-10-01T10:00:00Z");
        agronomoDepoisId = usuario("agro-depois@v19.test", "AGRONOMO", "ATIVA", "2026-10-02T10:00:00Z");
        pendenteId = usuario("pendente@v19.test", "PRODUTOR_RURAL", "PENDENTE", "2026-10-03T10:00:00Z");
        propriedadeDepoisId = UUID.randomUUID();
        jdbc.update("INSERT INTO " + SCHEMA + ".propriedades (id, nome, proprietario_id) VALUES (?, 'Fazenda Nova', ?)",
                propriedadeDepoisId, produtorDepoisId);
    }

    @AfterEach
    void limpar() {
        flyway("19").clean();
    }

    private UUID usuario(String email, String papel, String status, String criadoEm) {
        UUID id = UUID.randomUUID();
        String hash = "PENDENTE".equals(status) ? null : "hash-" + email;
        jdbc.update("INSERT INTO " + SCHEMA + ".usuarios (id, nome, email, senha_hash, papel, criado_em, status) "
                        + "VALUES (?, ?, ?, ?, ?::" + SCHEMA + ".papel_usuario, ?::timestamptz, ?)",
                id, email, email, hash, papel, criadoEm, status);
        return id;
    }

    private String texto(String tabela, String coluna, UUID id) {
        return jdbc.queryForObject("SELECT " + coluna + "::text FROM " + SCHEMA + "." + tabela + " WHERE id = ?", String.class, id);
    }

    private String grupoInicial() {
        return jdbc.queryForObject("SELECT id::text FROM " + SCHEMA + ".grupos", String.class);
    }

    @Test
    void oGestorQueGereOGrupoInicialViraMasterEOsOutrosGestoresContinuamAdmins() {
        flyway("20").migrate();

        assertThat(texto("usuarios", "papel", gestorId)).isEqualTo("ADMIN_MASTER");
        assertThat(texto("usuarios", "papel", gestorMaisNovoId)).isEqualTo("GESTOR_TECNOLOGIA");
        assertThat(jdbc.queryForObject("SELECT admin_id FROM " + SCHEMA + ".grupos", UUID.class)).isEqualTo(gestorId);
    }

    @Test
    void completaOGrupoDeQuemFoiCriadoDepoisDaV19() {
        flyway("20").migrate();
        String grupo = grupoInicial();

        assertThat(texto("usuarios", "grupo_id", produtorDepoisId)).isEqualTo(grupo);
        assertThat(texto("propriedades", "grupo_id", propriedadeDepoisId)).isEqualTo(grupo);
        assertThat(jdbc.queryForList("SELECT grupo_id::text FROM " + SCHEMA + ".agronomo_grupos WHERE agronomo_id = ?",
                String.class, agronomoDepoisId)).containsExactly(grupo);
    }

    @Test
    void cadastroPendenteContinuaSemGrupoEQuemJaTinhaGrupoNaoMuda() {
        String grupoAntes = texto("usuarios", "grupo_id", produtorAntesId);

        flyway("20").migrate();

        assertThat(texto("usuarios", "grupo_id", pendenteId)).isNull();
        assertThat(texto("usuarios", "grupo_id", produtorAntesId)).isEqualTo(grupoAntes);
    }

    @Test
    void naoMudaSenhaEstadoNemVersaoDeSessaoDeNinguem() {
        flyway("20").migrate();

        assertThat(texto("usuarios", "senha_hash", gestorId)).isEqualTo("hash-gestor@v19.test");
        assertThat(texto("usuarios", "status", pendenteId)).isEqualTo("PENDENTE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".usuarios WHERE versao_sessao <> 0", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".usuarios", Long.class)).isEqualTo(6);
    }

    @Test
    void agronomoNaoPodeTerGrupoProprioDepoisDaV20() {
        flyway("20").migrate();

        assertThatThrownBy(() -> jdbc.update("UPDATE " + SCHEMA + ".usuarios SET grupo_id = ?::uuid WHERE id = ?",
                grupoInicial(), agronomoDepoisId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoSemGrupoNaoQuebraENaoPromoveNinguem() {
        jdbc.update("UPDATE " + SCHEMA + ".usuarios SET grupo_id = NULL");
        jdbc.update("UPDATE " + SCHEMA + ".propriedades SET grupo_id = NULL");
        jdbc.update("DELETE FROM " + SCHEMA + ".agronomo_grupos");
        jdbc.update("DELETE FROM " + SCHEMA + ".grupos");

        flyway("20").migrate();

        List<String> papeis = jdbc.queryForList("SELECT papel::text FROM " + SCHEMA + ".usuarios WHERE papel::text LIKE '%GESTOR%' OR papel::text LIKE '%MASTER%'", String.class);
        assertThat(papeis).containsOnly("GESTOR_TECNOLOGIA");
    }
}
