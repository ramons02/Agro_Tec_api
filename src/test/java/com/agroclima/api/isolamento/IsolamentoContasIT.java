package com.agroclima.api.isolamento;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.StatusConta;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.grupo.ParticipacaoAgronomo;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Isolamento nas rotas de GESTAO de contas (spec 024, FR-004, FR-016, SC-002). */
class IsolamentoContasIT extends IsolamentoBase {

    @SuppressWarnings("unchecked")
    private List<String> emails(ResponseEntity<Map> lista) {
        return ((List<Map<String, Object>>) dados(lista).get("itens")).stream()
                .map(i -> (String) i.get("email")).toList();
    }

    private void assertNaoEncontrada(ResponseEntity<Map> resposta, String acao) {
        assertThat(resposta.getStatusCode()).as(acao).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listaEResumoDoAdminContamSoOGrupoDeleENuncaOMaster() {
        List<String> doAdminA = emails(chamar(HttpMethod.GET, "/api/v1/usuarios?page_size=100", adminA, null));

        assertThat(doAdminA).containsExactlyInAnyOrder(
                adminA.getEmail(), produtorA.getEmail(), agronomoA.getEmail());
        assertThat(doAdminA).doesNotContain(master.getEmail(), adminB.getEmail(), produtorB.getEmail(), agronomoB.getEmail());
        Map<String, Object> resumo = dados(chamar(HttpMethod.GET, "/api/v1/usuarios/resumo", adminA, null));
        assertThat(((Number) resumo.get("total")).intValue()).isEqualTo(3);
    }

    @Test
    void masterVeTodasAsContas() {
        List<String> todas = emails(chamar(HttpMethod.GET, "/api/v1/usuarios?page_size=100", master, null));

        assertThat(todas).contains(master.getEmail(), adminA.getEmail(), adminB.getEmail(),
                produtorA.getEmail(), produtorB.getEmail(), agronomoA.getEmail(), agronomoB.getEmail());
        assertThat(((Number) dados(chamar(HttpMethod.GET, "/api/v1/usuarios/resumo", master, null)).get("total")).intValue())
                .isEqualTo(7);
    }

    @Test
    void adminNaoVeNemMexeEmContaDeOutroGrupo() {
        String base = "/api/v1/usuarios/" + produtorB.getId();

        assertNaoEncontrada(chamar(HttpMethod.GET, base, adminA, null), "detalhe");
        assertNaoEncontrada(chamar(HttpMethod.PATCH, base, adminA, Map.of("nome", "Invadido")), "editar");
        assertNaoEncontrada(chamar(HttpMethod.POST, base + "/suspender", adminA, null), "suspender");
        assertNaoEncontrada(chamar(HttpMethod.POST, base + "/reativar", adminA, null), "reativar");
        assertNaoEncontrada(chamar(HttpMethod.POST, base + "/redefinir-senha", adminA, null), "redefinir senha");
        assertNaoEncontrada(chamar(HttpMethod.POST, base + "/confirmar", adminA, null), "confirmar");
        assertNaoEncontrada(chamar(HttpMethod.DELETE, base, adminA, null), "recusar");
        Usuario depois = usuarioRepository.findById(produtorB.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusConta.ATIVA);
        assertThat(depois.getNome()).isEqualTo(produtorB.getNome());
        assertThat(depois.getSenhaHash()).isEqualTo(produtorB.getSenhaHash());
    }

    @Test
    void adminNaoVeNemMexeNoMaster() {
        String base = "/api/v1/usuarios/" + master.getId();

        assertNaoEncontrada(chamar(HttpMethod.GET, base, adminA, null), "detalhe do Master");
        assertNaoEncontrada(chamar(HttpMethod.POST, base + "/suspender", adminA, null), "suspender o Master");
        assertNaoEncontrada(chamar(HttpMethod.POST, base + "/redefinir-senha", adminA, null), "redefinir senha do Master");
        assertThat(usuarioRepository.findById(master.getId()).orElseThrow().getStatus()).isEqualTo(StatusConta.ATIVA);
    }

    @Test
    void cadastroPendenteSemGrupoSoOMasterVe() {
        Usuario pendente = usuarioRepository.save(
                Usuario.pendente("Pendente", "pendente@iso.test", Papel.PRODUTOR_RURAL, Instant.now()));

        assertThat(emails(chamar(HttpMethod.GET, "/api/v1/usuarios?page_size=100", adminA, null)))
                .doesNotContain(pendente.getEmail());
        assertNaoEncontrada(chamar(HttpMethod.POST, "/api/v1/usuarios/" + pendente.getId() + "/confirmar", adminA, null),
                "confirmar pendente sem grupo");
        assertThat(emails(chamar(HttpMethod.GET, "/api/v1/usuarios?page_size=100", master, null)))
                .contains(pendente.getEmail());
    }

    @Test
    void masterConfirmaPendenteEscolhendoOGrupoEAContaEntraNele() {
        Usuario pendente = usuarioRepository.save(
                Usuario.pendente("Pendente B", "pendente-b@iso.test", Papel.PRODUTOR_RURAL, Instant.now()));

        ResponseEntity<Map> resposta = chamar(HttpMethod.POST, "/api/v1/usuarios/" + pendente.getId() + "/confirmar",
                master, Map.of("grupo_id", grupoB.getId().toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(usuarioRepository.findById(pendente.getId()).orElseThrow().getGrupoId()).isEqualTo(grupoB.getId());
        assertThat(emails(chamar(HttpMethod.GET, "/api/v1/usuarios?page_size=100", adminB, null))).contains(pendente.getEmail());
        assertThat(emails(chamar(HttpMethod.GET, "/api/v1/usuarios?page_size=100", adminA, null))).doesNotContain(pendente.getEmail());
    }

    @Test
    void adminCriaContaSoNoGrupoDeleEMasterEscolheOGrupo() {
        ResponseEntity<Map> doAdmin = chamar(HttpMethod.POST, "/api/v1/usuarios", adminA,
                Map.of("nome", "Nova do A", "email", "nova-a@iso.test", "papel", "PRODUTOR_RURAL"));
        ResponseEntity<Map> paraOutroGrupo = chamar(HttpMethod.POST, "/api/v1/usuarios", adminA,
                Map.of("nome", "Intrusa", "email", "intrusa@iso.test", "papel", "PRODUTOR_RURAL",
                        "grupo_id", grupoB.getId().toString()));
        ResponseEntity<Map> doMaster = chamar(HttpMethod.POST, "/api/v1/usuarios", master,
                Map.of("nome", "Nova do B", "email", "nova-b@iso.test", "papel", "AGRONOMO",
                        "grupo_id", grupoB.getId().toString()));

        assertThat(doAdmin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(usuarioRepository.findByEmail("nova-a@iso.test").orElseThrow().getGrupoId()).isEqualTo(grupoA.getId());
        assertThat(paraOutroGrupo.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(usuarioRepository.findByEmail("intrusa@iso.test")).isEmpty();
        assertThat(doMaster.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Usuario agronomo = usuarioRepository.findByEmail("nova-b@iso.test").orElseThrow();
        assertThat(agronomo.getGrupoId()).as("agronomo nunca tem grupo proprio").isNull();
        assertThat(participacaoRepository.findByIdAgronomoId(agronomo.getId()))
                .extracting(ParticipacaoAgronomo::getGrupoId).containsExactly(grupoB.getId());
    }

    @Test
    void adminNaoCriaAdminNemMaster() {
        for (String papel : List.of("GESTOR_TECNOLOGIA", "ADMIN_MASTER")) {
            ResponseEntity<Map> resposta = chamar(HttpMethod.POST, "/api/v1/usuarios", adminA,
                    Map.of("nome", "X", "email", papel.toLowerCase() + "@iso.test", "papel", papel));

            assertThat(resposta.getStatusCode()).as(papel).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void adminNaoConvidaGestorSoOMaster() {
        ResponseEntity<Map> doAdmin = chamar(HttpMethod.POST, "/api/v1/auth/convites-gestor", adminA, Map.of("email", "novo@iso.test"));
        ResponseEntity<Map> doMaster = chamar(HttpMethod.POST, "/api/v1/auth/convites-gestor", master, Map.of("email", "novo@iso.test"));

        assertThat(doAdmin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(doMaster.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void cadastroPublicoNaoCriaMaster() {
        ResponseEntity<Map> resposta = restTemplate.postForEntity("/api/v1/auth/registro",
                Map.of("nome", "Invasor", "email", "invasor@iso.test", "papel", "ADMIN_MASTER"), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(usuarioRepository.findByEmail("invasor@iso.test")).isEmpty();
    }

    @Test
    void agronomoDeMaisDeUmGrupoSoOMasterSuspendeRedefineEEdita() {
        participacaoRepository.save(new ParticipacaoAgronomo(agronomoA.getId(), grupoB.getId(), Instant.now()));
        String base = "/api/v1/usuarios/" + agronomoA.getId();

        assertThat(chamar(HttpMethod.POST, base + "/suspender", adminA, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.POST, base + "/redefinir-senha", adminA, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.PATCH, base, adminA, Map.of("nome", "Outro")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.POST, base + "/suspender", master, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        // O agronomo aparece na lista dos dois Admins, cada um com os dois grupos informados.
        assertThat(emails(chamar(HttpMethod.GET, "/api/v1/usuarios?page_size=100", adminB, null))).contains(agronomoA.getEmail());
    }

    @Test
    void ultimoMasterAtivoNaoPodeSerSuspenso() {
        ResponseEntity<Map> resposta = chamar(HttpMethod.POST, "/api/v1/usuarios/" + master.getId() + "/suspender", master, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void suspenderUmAdminNaoDerrubaAsContasDoGrupoDele() {
        ResponseEntity<Map> resposta = chamar(HttpMethod.POST, "/api/v1/usuarios/" + adminA.getId() + "/suspender", master, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(usuarioRepository.findById(produtorA.getId()).orElseThrow().getStatus()).isEqualTo(StatusConta.ATIVA);
        assertThat(usuarioRepository.findById(agronomoA.getId()).orElseThrow().getStatus()).isEqualTo(StatusConta.ATIVA);
        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades", produtorA, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void produtorEAgronomoNaoAcessamAGestaoDeContas() {
        for (Usuario usuario : List.of(produtorA, agronomoA)) {
            assertThat(chamar(HttpMethod.GET, "/api/v1/usuarios", usuario, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(chamar(HttpMethod.GET, "/api/v1/usuarios/resumo", usuario, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
