package com.agroclima.api.business.grupo;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.StatusConta;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.isolamento.IsolamentoBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** US6 (spec 024): Master novo so por convite de um Master (uso unico, amarrado ao e-mail); nunca o ultimo ativo cai. */
class MasterPorConviteIT extends IsolamentoBase {

    @Autowired
    private JdbcTemplate jdbc;

    private String conviteDe(Usuario como, String email) {
        ResponseEntity<Map> convite = chamar(HttpMethod.POST, "/api/v1/auth/convites-master", como, Map.of("email", email));
        assertThat(convite.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String link = (String) dados(convite).get("link");
        return link.substring(link.indexOf("convite=") + "convite=".length());
    }

    private ResponseEntity<Map> cadastrar(String email, String papel, String convite) {
        Map<String, Object> corpo = new java.util.HashMap<>(Map.of(
                "nome", "Novo Master", "email", email, "senha", "senha12345", "papel", papel));
        if (convite != null) {
            corpo.put("convite", convite);
        }
        return restTemplate.postForEntity("/api/v1/auth/registro", corpo, Map.class);
    }

    @Test
    void oConviteCriaUmMasterAtivoComSenhaEleEntra() {
        String convite = conviteDe(master, "novo-master@iso.test");

        ResponseEntity<Map> resposta = cadastrar("novo-master@iso.test", "ADMIN_MASTER", convite);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Usuario novo = usuarioRepository.findByEmail("novo-master@iso.test").orElseThrow();
        assertThat(novo.getPapel()).isEqualTo(Papel.ADMIN_MASTER);
        assertThat(novo.getStatus()).isEqualTo(StatusConta.ATIVA);
        assertThat(restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "novo-master@iso.test", "senha", "senha12345"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades", novo, null).getBody().toString())
                .contains("Fazenda A").contains("Fazenda B");
    }

    @Test
    void oConviteSoServeUmaVezESoParaOEmailConvidado() {
        String convite = conviteDe(master, "unico@iso.test");

        ResponseEntity<Map> outroEmail = cadastrar("outro@iso.test", "ADMIN_MASTER", convite);
        ResponseEntity<Map> primeiro = cadastrar("unico@iso.test", "ADMIN_MASTER", convite);
        ResponseEntity<Map> reuso = cadastrar("unico@iso.test", "ADMIN_MASTER", convite);

        assertThat(outroEmail.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(primeiro.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reuso.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminProdutorECadastroPublicoNaoCriamMaster() {
        for (Usuario usuario : java.util.List.of(adminA, produtorA, agronomoA)) {
            assertThat(chamar(HttpMethod.POST, "/api/v1/auth/convites-master", usuario, Map.of("email", "x@iso.test")).getStatusCode())
                    .as(usuario.getEmail()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(cadastrar("sem-convite@iso.test", "ADMIN_MASTER", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(cadastrar("convite-falso@iso.test", "ADMIN_MASTER", "falso").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void oConviteNaoServePraCriarAdmin() {
        String convite = conviteDe(master, "quer-ser-admin@iso.test");

        ResponseEntity<Map> resposta = cadastrar("quer-ser-admin@iso.test", "GESTOR_TECNOLOGIA", convite);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(usuarioRepository.findByEmail("quer-ser-admin@iso.test")).isEmpty();
    }

    @Test
    void conviteAntigoDeGestorNaoCriaMaster() {
        jdbc.update("INSERT INTO convites_gestor (id, token_hash, email, criado_por, criado_em, expira_em, papel_destino) "
                        + "VALUES (?, ?, 'antigo@iso.test', ?, now(), now() + interval '1 day', 'GESTOR_TECNOLOGIA')",
                UUID.randomUUID(), hashDe("token-antigo"), master.getId());

        ResponseEntity<Map> resposta = cadastrar("antigo@iso.test", "ADMIN_MASTER", "token-antigo");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void comDoisMastersUmSuspendeOOutroMasOUltimoAtivoNaoCai() {
        String convite = conviteDe(master, "segundo@iso.test");
        cadastrar("segundo@iso.test", "ADMIN_MASTER", convite);
        Usuario segundo = usuarioRepository.findByEmail("segundo@iso.test").orElseThrow();

        ResponseEntity<Map> suspende = chamar(HttpMethod.POST, "/api/v1/usuarios/" + segundo.getId() + "/suspender", master, null);
        ResponseEntity<Map> ultimo = chamar(HttpMethod.POST, "/api/v1/usuarios/" + master.getId() + "/suspender", master, null);

        assertThat(suspende.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ultimo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(usuarioRepository.findById(master.getId()).orElseThrow().getStatus()).isEqualTo(StatusConta.ATIVA);
    }

    private static String hashDe(String token) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
