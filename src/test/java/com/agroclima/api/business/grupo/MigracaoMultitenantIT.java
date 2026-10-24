package com.agroclima.api.business.grupo;

import com.agroclima.api.support.IntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 024, fase 1 (US7): caminho de atualizacao de um banco que ja esta na V17 (o estado de producao antes
 * da hierarquia). Roda as migrations num schema proprio, insere os dados antigos, aplica a V18 e a V19 e confere
 * que o grupo inicial recebe tudo, sem mudar papel, senha nem estado de ninguem.
 */
class MigracaoMultitenantIT extends IntegrationTestBase {

    private static final String SCHEMA = "java_migracao_it";

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    private UUID gestorId;
    private UUID gestorMaisNovoId;
    private UUID produtorId;
    private UUID produtorPendenteId;
    private UUID agronomoId;
    private UUID propriedadeId;

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
    void prepararBancoNaV17() {
        jdbc = new JdbcTemplate(dataSource);
        flyway("17").clean();
        flyway("17").migrate();

        gestorId = usuario("gestor@antigo.test", "GESTOR_TECNOLOGIA", "ATIVA", "2026-01-01T10:00:00Z");
        gestorMaisNovoId = usuario("gestor2@antigo.test", "GESTOR_TECNOLOGIA", "ATIVA", "2026-06-01T10:00:00Z");
        produtorId = usuario("produtor@antigo.test", "PRODUTOR_RURAL", "ATIVA", "2026-02-01T10:00:00Z");
        produtorPendenteId = usuario("pendente@antigo.test", "PRODUTOR_RURAL", "PENDENTE", "2026-09-01T10:00:00Z");
        agronomoId = usuario("agro@antigo.test", "AGRONOMO", "ATIVA", "2026-03-01T10:00:00Z");
        propriedadeId = UUID.randomUUID();
        jdbc.update("INSERT INTO " + SCHEMA + ".propriedades (id, nome, proprietario_id) VALUES (?, 'Fazenda Antiga', ?)",
                propriedadeId, produtorId);
    }

    @AfterEach
    void limpar() {
        flyway("17").clean();
    }

    private UUID usuario(String email, String papel, String status, String criadoEm) {
        UUID id = UUID.randomUUID();
        // senha_hash nulo so vale para PENDENTE (CHECK da V16).
        String hash = "PENDENTE".equals(status) ? null : "hash-" + email;
        jdbc.update("INSERT INTO " + SCHEMA + ".usuarios (id, nome, email, senha_hash, papel, criado_em, status) "
                        + "VALUES (?, ?, ?, ?, ?::" + SCHEMA + ".papel_usuario, ?::timestamptz, ?)",
                id, email, email, hash, papel, criadoEm, status);
        return id;
    }

    private void migrarParaOsGrupos() {
        flyway("19").migrate();
    }

    private String texto(String tabela, String coluna, UUID id) {
        return jdbc.queryForObject("SELECT " + coluna + "::text FROM " + SCHEMA + "." + tabela + " WHERE id = ?", String.class, id);
    }

    @Test
    void criaOGrupoInicialSobOGestorMaisAntigo() {
        migrarParaOsGrupos();

        List<Map<String, Object>> grupos = jdbc.queryForList("SELECT * FROM " + SCHEMA + ".grupos");

        assertThat(grupos).singleElement().satisfies(g -> {
            assertThat(g.get("nome")).isEqualTo("Grupo inicial");
            assertThat(g.get("admin_id")).isEqualTo(gestorId);
            assertThat((String) g.get("codigo_cadastro_hash")).hasSize(64);
        });
    }

    @Test
    void produtoresEGestoresAtivosEntramNoGrupoPendenteFicaSemGrupo() {
        migrarParaOsGrupos();
        String grupo = jdbc.queryForObject("SELECT id::text FROM " + SCHEMA + ".grupos", String.class);

        assertThat(texto("usuarios", "grupo_id", produtorId)).isEqualTo(grupo);
        assertThat(texto("usuarios", "grupo_id", gestorId)).isEqualTo(grupo);
        assertThat(texto("usuarios", "grupo_id", gestorMaisNovoId)).isEqualTo(grupo);
        assertThat(texto("usuarios", "grupo_id", produtorPendenteId)).isNull();
    }

    @Test
    void agronomoAtivoGanhaParticipacaoNoGrupoInicialESemGrupoProprio() {
        migrarParaOsGrupos();

        List<Map<String, Object>> participacoes = jdbc.queryForList(
                "SELECT agronomo_id, grupo_id::text AS grupo FROM " + SCHEMA + ".agronomo_grupos");

        assertThat(participacoes).singleElement().satisfies(p -> assertThat(p.get("agronomo_id")).isEqualTo(agronomoId));
        assertThat(texto("usuarios", "grupo_id", agronomoId)).isNull();
    }

    @Test
    void propriedadeAcompanhaOGrupoDoDono() {
        migrarParaOsGrupos();
        String grupo = jdbc.queryForObject("SELECT id::text FROM " + SCHEMA + ".grupos", String.class);

        assertThat(texto("propriedades", "grupo_id", propriedadeId)).isEqualTo(grupo);
    }

    @Test
    void naoMudaPapelSenhaNemEstadoDeNinguem() {
        migrarParaOsGrupos();

        assertThat(texto("usuarios", "papel", gestorId)).isEqualTo("GESTOR_TECNOLOGIA");
        assertThat(texto("usuarios", "papel", produtorId)).isEqualTo("PRODUTOR_RURAL");
        assertThat(texto("usuarios", "senha_hash", produtorId)).isEqualTo("hash-produtor@antigo.test");
        assertThat(texto("usuarios", "status", produtorPendenteId)).isEqualTo("PENDENTE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".usuarios WHERE versao_sessao <> 0", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".usuarios", Long.class)).isEqualTo(5);
    }

    @Test
    void oValorAdminMasterExisteNoEnumMasNinguemOTem() {
        migrarParaOsGrupos();

        List<String> valores = jdbc.queryForList(
                "SELECT unnest(enum_range(NULL::" + SCHEMA + ".papel_usuario))::text", String.class);

        assertThat(valores).contains("ADMIN_MASTER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".usuarios WHERE papel = 'ADMIN_MASTER'", Long.class)).isZero();
    }

    @Test
    void conviteExistenteContinuaCriandoGestor() {
        jdbc.update("INSERT INTO " + SCHEMA + ".convites_gestor (id, token_hash, email, criado_por, criado_em, expira_em) "
                + "VALUES (?, 'hash-do-token', 'novo@antigo.test', ?, now(), now() + interval '1 day')", UUID.randomUUID(), gestorId);

        migrarParaOsGrupos();

        assertThat(jdbc.queryForObject("SELECT papel_destino FROM " + SCHEMA + ".convites_gestor", String.class))
                .isEqualTo("GESTOR_TECNOLOGIA");
    }

    @Test
    void bancoSemGestorNaoCriaGrupoNemQuebra() {
        jdbc.update("DELETE FROM " + SCHEMA + ".propriedades");
        jdbc.update("DELETE FROM " + SCHEMA + ".usuarios WHERE papel = 'GESTOR_TECNOLOGIA'");

        migrarParaOsGrupos();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".grupos", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".usuarios WHERE grupo_id IS NOT NULL", Long.class)).isZero();
    }

    @Test
    void auditoriaAceitaAsNovasAcoesDeGrupo() {
        migrarParaOsGrupos();

        for (String acao : List.of("CRIAR_ADMIN", "MOVER_GRUPO", "REATRIBUIR_GRUPO", "REMOVER_DO_GRUPO", "NOVO_LINK_GRUPO")) {
            jdbc.update("INSERT INTO " + SCHEMA + ".auditoria_contas (id, ator_id, acao, alvo_email, criado_em, alvo_grupo_id) "
                    + "VALUES (?, ?, ?, 'x@teste.com', now(), ?)", UUID.randomUUID(), gestorId, acao, UUID.randomUUID());
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".auditoria_contas", Long.class)).isEqualTo(5);
    }
}
