package com.agroclima.api.business.grupo;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.StatusConta;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.usuario.AcaoAuditoria;
import com.agroclima.api.business.usuario.AuditoriaConta;
import com.agroclima.api.business.usuario.AuditoriaContaRepository;
import com.agroclima.api.isolamento.IsolamentoBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US4 (spec 024): cadastro aberto por grupo. Sem link, o pendente e do Master, que atribui o grupo; pelo link do
 * grupo de um Admin, o pendente ja cai nesse grupo e o Admin decide. O link e permanente, so existe o hash, e gerar
 * outro invalida o anterior na hora.
 */
class CadastroPorLinkIT extends IsolamentoBase {

    @Autowired
    private AuditoriaContaRepository auditoriaRepository;

    private ResponseEntity<Map> cadastrar(String email, String papel, String codigoDoGrupo, String origem) {
        Map<String, Object> corpo = new HashMap<>(Map.of("nome", "Pessoa " + email, "email", email, "papel", papel));
        if (codigoDoGrupo != null) {
            corpo.put("grupo", codigoDoGrupo);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", origem);
        return restTemplate.exchange("/api/v1/auth/registro", HttpMethod.POST, new HttpEntity<>(corpo, headers), Map.class);
    }

    private String novoLinkDe(Usuario como, Grupo grupo) {
        ResponseEntity<Map> resposta = chamar(HttpMethod.POST, "/api/v1/grupos/" + grupo.getId() + "/novo-link", como, null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        String link = (String) dados(resposta).get("link");
        return link.substring(link.indexOf("grupo=") + "grupo=".length());
    }

    @SuppressWarnings("unchecked")
    private List<String> emails(Usuario como) {
        return ((List<Map<String, Object>>) dados(chamar(HttpMethod.GET, "/api/v1/usuarios?page_size=100", como, null)).get("itens"))
                .stream().map(i -> (String) i.get("email")).toList();
    }

    @Test
    void cadastroSemLinkFicaPendenteSemGrupoESoOMasterVe() {
        cadastrar("sem-link@iso.test", "PRODUTOR_RURAL", null, "198.51.100.1");

        Usuario pendente = usuarioRepository.findByEmail("sem-link@iso.test").orElseThrow();
        assertThat(pendente.getStatus()).isEqualTo(StatusConta.PENDENTE);
        assertThat(pendente.getGrupoId()).isNull();
        assertThat(emails(master)).contains("sem-link@iso.test");
        assertThat(emails(adminA)).doesNotContain("sem-link@iso.test");
        assertThat(emails(adminB)).doesNotContain("sem-link@iso.test");
    }

    @Test
    void masterPrecisaEscolherOGrupoAoConfirmarUmPendenteSemGrupo() {
        cadastrar("atribuir@iso.test", "PRODUTOR_RURAL", null, "198.51.100.2");
        Usuario pendente = usuarioRepository.findByEmail("atribuir@iso.test").orElseThrow();
        String confirmar = "/api/v1/usuarios/" + pendente.getId() + "/confirmar";

        ResponseEntity<Map> semGrupo = chamar(HttpMethod.POST, confirmar, master, null);
        ResponseEntity<Map> grupoInexistente = chamar(HttpMethod.POST, confirmar, master, Map.of("grupo_id", java.util.UUID.randomUUID().toString()));
        ResponseEntity<Map> comGrupo = chamar(HttpMethod.POST, confirmar, master, Map.of("grupo_id", grupoB.getId().toString()));

        assertThat(semGrupo.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(grupoInexistente.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(comGrupo.getStatusCode()).isEqualTo(HttpStatus.OK);
        Usuario depois = usuarioRepository.findById(pendente.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusConta.ATIVA);
        assertThat(depois.getGrupoId()).isEqualTo(grupoB.getId());
    }

    @Test
    void cadastroPeloLinkDoGrupoCaiPendenteNeleEOAdminDeleEOMasterVeMasNaoOOutroAdmin() {
        String codigo = novoLinkDe(adminA, grupoA);

        ResponseEntity<Map> resposta = cadastrar("pelo-link@iso.test", "PRODUTOR_RURAL", codigo, "198.51.100.3");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Usuario pendente = usuarioRepository.findByEmail("pelo-link@iso.test").orElseThrow();
        assertThat(pendente.getStatus()).isEqualTo(StatusConta.PENDENTE);
        assertThat(pendente.getGrupoId()).isEqualTo(grupoA.getId());
        assertThat(emails(adminA)).contains("pelo-link@iso.test");
        assertThat(emails(master)).contains("pelo-link@iso.test");
        assertThat(emails(adminB)).doesNotContain("pelo-link@iso.test");
    }

    @Test
    void oPendentePeloLinkNaoEntraAntesDaConfirmacao() {
        String codigo = novoLinkDe(adminA, grupoA);
        cadastrar("espera@iso.test", "PRODUTOR_RURAL", codigo, "198.51.100.4");

        ResponseEntity<Map> login = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "espera@iso.test", "senha", "qualquer12345"), Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void oAdminDoGrupoConfirmaSemInformarGrupoEARecusaOsDoGrupoDele() {
        String codigo = novoLinkDe(adminA, grupoA);
        cadastrar("confirma@iso.test", "PRODUTOR_RURAL", codigo, "198.51.100.5");
        cadastrar("recusa@iso.test", "PRODUTOR_RURAL", codigo, "198.51.100.5");
        Usuario confirma = usuarioRepository.findByEmail("confirma@iso.test").orElseThrow();
        Usuario recusa = usuarioRepository.findByEmail("recusa@iso.test").orElseThrow();

        ResponseEntity<Map> confirmada = chamar(HttpMethod.POST, "/api/v1/usuarios/" + confirma.getId() + "/confirmar", adminA, null);
        ResponseEntity<Map> recusada = chamar(HttpMethod.DELETE, "/api/v1/usuarios/" + recusa.getId(), adminA, null);

        assertThat(confirmada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dados(confirmada).get("senha_temporaria")).isNotNull();
        assertThat(usuarioRepository.findById(confirma.getId()).orElseThrow().getGrupoId()).isEqualTo(grupoA.getId());
        assertThat(recusada.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(usuarioRepository.findById(recusa.getId())).isEmpty();
    }

    @Test
    void oOutroAdminNaoConfirmaNemRecusaOPendenteDoGrupoAlheio() {
        String codigo = novoLinkDe(adminA, grupoA);
        cadastrar("alheio@iso.test", "PRODUTOR_RURAL", codigo, "198.51.100.6");
        Usuario pendente = usuarioRepository.findByEmail("alheio@iso.test").orElseThrow();

        assertThat(chamar(HttpMethod.POST, "/api/v1/usuarios/" + pendente.getId() + "/confirmar", adminB, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chamar(HttpMethod.DELETE, "/api/v1/usuarios/" + pendente.getId(), adminB, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(usuarioRepository.findById(pendente.getId()).orElseThrow().getStatus()).isEqualTo(StatusConta.PENDENTE);
    }

    @Test
    void agronomoPeloLinkGanhaParticipacaoNoGrupoEAdminConfirma() {
        String codigo = novoLinkDe(adminA, grupoA);
        cadastrar("agro-link@iso.test", "AGRONOMO", codigo, "198.51.100.7");
        Usuario pendente = usuarioRepository.findByEmail("agro-link@iso.test").orElseThrow();

        assertThat(pendente.getGrupoId()).as("agronomo nunca tem grupo proprio").isNull();
        assertThat(participacaoRepository.findByIdAgronomoId(pendente.getId()))
                .extracting(ParticipacaoAgronomo::getGrupoId).containsExactly(grupoA.getId());
        assertThat(emails(adminA)).contains("agro-link@iso.test");
        assertThat(emails(adminB)).doesNotContain("agro-link@iso.test");
        assertThat(chamar(HttpMethod.POST, "/api/v1/usuarios/" + pendente.getId() + "/confirmar", adminA, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(usuarioRepository.findById(pendente.getId()).orElseThrow().getStatus()).isEqualTo(StatusConta.ATIVA);
    }

    @Test
    void novoLinkInvalidaOAnteriorNaHora() {
        String antigo = novoLinkDe(adminA, grupoA);
        String novo = novoLinkDe(adminA, grupoA);

        ResponseEntity<Map> comAntigo = cadastrar("antigo@iso.test", "PRODUTOR_RURAL", antigo, "198.51.100.8");
        ResponseEntity<Map> comNovo = cadastrar("novo@iso.test", "PRODUTOR_RURAL", novo, "198.51.100.8");

        assertThat(antigo).isNotEqualTo(novo);
        assertThat(comAntigo.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(usuarioRepository.findByEmail("antigo@iso.test")).isEmpty();
        assertThat(comNovo.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void linkInventadoOuVazioEhRecusadoSemCriarConta() {
        assertThat(cadastrar("inventado@iso.test", "PRODUTOR_RURAL", "codigo-que-nao-existe", "198.51.100.9").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(usuarioRepository.findByEmail("inventado@iso.test")).isEmpty();
    }

    @Test
    void soOHashDoCodigoEGuardadoEOLinkNaoVoltaEmNenhumaConsulta() {
        String codigo = novoLinkDe(adminA, grupoA);

        Grupo guardado = grupoRepository.findById(grupoA.getId()).orElseThrow();

        assertThat(guardado.getCodigoCadastroHash()).hasSize(64).isNotEqualTo(codigo);
        String grupos = chamar(HttpMethod.GET, "/api/v1/grupos", master, null).getBody().toString();
        String usuarios = chamar(HttpMethod.GET, "/api/v1/usuarios?page_size=100", master, null).getBody().toString();
        assertThat(grupos).doesNotContain(codigo);
        assertThat(usuarios).doesNotContain(codigo);
    }

    @Test
    void masterGeraOLinkDeQualquerGrupoMasOAdminSoDoDele() {
        assertThat(chamar(HttpMethod.POST, "/api/v1/grupos/" + grupoB.getId() + "/novo-link", master, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(chamar(HttpMethod.POST, "/api/v1/grupos/" + grupoB.getId() + "/novo-link", adminA, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        for (Usuario usuario : List.of(produtorA, agronomoA)) {
            assertThat(chamar(HttpMethod.POST, "/api/v1/grupos/" + grupoA.getId() + "/novo-link", usuario, null).getStatusCode())
                    .as(usuario.getEmail()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void gerarOLinkFicaNaAuditoriaSemOCodigo() {
        String codigo = novoLinkDe(adminA, grupoA);

        List<AuditoriaConta> trilha = auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(adminA.getId());

        assertThat(trilha).singleElement().satisfies(a -> {
            assertThat(a.getAcao()).isEqualTo(AcaoAuditoria.NOVO_LINK_GRUPO);
            assertThat(a.getAtorId()).isEqualTo(adminA.getId());
            assertThat(a.getAlvoGrupoId()).isEqualTo(grupoA.getId());
            assertThat(String.valueOf(a.getDetalhe())).doesNotContain(codigo);
        });
    }

    @Test
    void produtorCadastradoPeloLinkNaoVeNadaDoOutroGrupoDepoisDeConfirmado() {
        String codigo = novoLinkDe(adminA, grupoA);
        cadastrar("isolado@iso.test", "PRODUTOR_RURAL", codigo, "198.51.100.10");
        Usuario pendente = usuarioRepository.findByEmail("isolado@iso.test").orElseThrow();
        chamar(HttpMethod.POST, "/api/v1/usuarios/" + pendente.getId() + "/confirmar", adminA, null);

        String propriedades = chamar(HttpMethod.GET, "/api/v1/propriedades", atual(pendente), null).getBody().toString();

        assertThat(propriedades).doesNotContain("Fazenda A").doesNotContain("Fazenda B");
        assertThat(usuarioRepository.findById(pendente.getId()).orElseThrow().getPapel()).isEqualTo(Papel.PRODUTOR_RURAL);
    }
}
