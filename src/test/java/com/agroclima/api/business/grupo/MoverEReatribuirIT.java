package com.agroclima.api.business.grupo;

import com.agroclima.api.business.auth.StatusConta;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.usuario.AcaoAuditoria;
import com.agroclima.api.business.usuario.AuditoriaConta;
import com.agroclima.api.business.usuario.AuditoriaContaRepository;
import com.agroclima.api.core.security.EstadoContaCache;
import com.agroclima.api.isolamento.IsolamentoBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** US2 (spec 024): o Master move usuarios e reatribui um grupo a outro Admin; o grupo nao cai ao suspender o Admin. */
class MoverEReatribuirIT extends IsolamentoBase {

    @Autowired
    private AuditoriaContaRepository auditoriaRepository;

    @Autowired
    private EstadoContaCache estadoContaCache;

    private ResponseEntity<Map> mover(Usuario como, Usuario alvo, Object corpo) {
        return chamar(HttpMethod.POST, "/api/v1/usuarios/" + alvo.getId() + "/mover", como, corpo);
    }

    private ResponseEntity<Map> reatribuir(Usuario como, Grupo origem, Grupo destino) {
        return chamar(HttpMethod.POST, "/api/v1/grupos/" + origem.getId() + "/reatribuir", como,
                Map.of("grupo_destino_id", destino.getId().toString()));
    }

    private HttpStatus statusDe(HttpMethod metodo, String caminho, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return HttpStatus.valueOf(restTemplate.exchange(caminho, metodo, new HttpEntity<>(headers), Map.class)
                .getStatusCode().value());
    }

    @Test
    void moverProdutorLevaAContaAsPropriedadesETalhoesEDerrubaAsSessoes() {
        String tokenAntigo = token(produtorA);

        ResponseEntity<Map> resposta = mover(master, produtorA, Map.of("grupo_id", grupoB.getId().toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(usuarioRepository.findById(produtorA.getId()).orElseThrow().getGrupoId()).isEqualTo(grupoB.getId());
        assertThat(propriedadeRepository.findById(propriedadeA.getId()).orElseThrow().getGrupoId()).isEqualTo(grupoB.getId());
        // O Admin B passa a ver a propriedade e o Admin A deixa de ver.
        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades/" + propriedadeA.getId(), adminB, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades/" + propriedadeA.getId(), adminA, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chamar(HttpMethod.GET, "/api/v1/talhoes/" + talhaoA.getId(), adminB, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(statusDe(HttpMethod.GET, "/api/v1/propriedades", tokenAntigo)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void moverAuditaComOGrupoDeDestino() {
        mover(master, produtorA, Map.of("grupo_id", grupoB.getId().toString()));

        List<AuditoriaConta> trilha = auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(produtorA.getId());

        assertThat(trilha).singleElement().satisfies(a -> {
            assertThat(a.getAcao()).isEqualTo(AcaoAuditoria.MOVER_GRUPO);
            assertThat(a.getAtorId()).isEqualTo(master.getId());
            assertThat(a.getAlvoGrupoId()).isEqualTo(grupoB.getId());
            assertThat(a.getDetalhe()).isEqualTo("de Grupo A para Grupo B");
        });
    }

    @Test
    void oVinculoDoAgronomoFicaPendenteAteOMasterIncluiLoNoGrupoNovo() {
        mover(master, produtorA, Map.of("grupo_id", grupoB.getId().toString()));

        // O agronomo A continua vinculado, mas nao pertence ao grupo B: sem acesso.
        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades/" + propriedadeA.getId(), agronomoA, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        participacaoRepository.save(new ParticipacaoAgronomo(agronomoA.getId(), grupoB.getId(), Instant.now()));
        estadoContaCache.invalidar(agronomoA.getId());

        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades/" + propriedadeA.getId(), agronomoA, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void moverAgronomoTrocaAParticipacaoEEncerraOsVinculosDaOrigem() {
        ResponseEntity<Map> resposta = mover(master, agronomoA, Map.of("grupo_id", grupoB.getId().toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(participacaoRepository.findByIdAgronomoId(agronomoA.getId()))
                .extracting(ParticipacaoAgronomo::getGrupoId).containsExactly(grupoB.getId());
        assertThat(vinculoRepository.findAll()).extracting(v -> v.getAgronomoId()).doesNotContain(agronomoA.getId());
        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades/" + propriedadeA.getId(), atual(agronomoA), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void agronomoDeVariosGruposPrecisaDizerDeQualGrupoSai() {
        participacaoRepository.save(new ParticipacaoAgronomo(agronomoA.getId(), grupoB.getId(), Instant.now()));
        Grupo terceiro = grupoRepository.save(new Grupo("Grupo C", adminB.getId(), "c".repeat(64), Instant.now()));

        ResponseEntity<Map> semOrigem = mover(master, agronomoA, Map.of("grupo_id", terceiro.getId().toString()));
        ResponseEntity<Map> comOrigem = mover(master, agronomoA,
                Map.of("grupo_id", terceiro.getId().toString(), "grupo_origem_id", grupoA.getId().toString()));

        assertThat(semOrigem.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(comOrigem.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(participacaoRepository.findByIdAgronomoId(agronomoA.getId()))
                .extracting(ParticipacaoAgronomo::getGrupoId).containsExactlyInAnyOrder(grupoB.getId(), terceiro.getId());
    }

    @Test
    void moverParaOMesmoGrupoAdminEMasterEGrupoInexistenteSaoRecusados() {
        assertThat(mover(master, produtorA, Map.of("grupo_id", grupoA.getId().toString())).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(mover(master, adminA, Map.of("grupo_id", grupoB.getId().toString())).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(mover(master, master, Map.of("grupo_id", grupoB.getId().toString())).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(mover(master, produtorA, Map.of("grupo_id", java.util.UUID.randomUUID().toString())).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void soOMasterMoveEReatribui() {
        for (Usuario usuario : List.of(adminA, produtorA, agronomoA)) {
            assertThat(mover(usuario, produtorB, Map.of("grupo_id", grupoA.getId().toString())).getStatusCode())
                    .as("mover como %s", usuario.getEmail()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(reatribuir(usuario, grupoB, grupoA).getStatusCode())
                    .as("reatribuir como %s", usuario.getEmail()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(usuarioRepository.findById(produtorB.getId()).orElseThrow().getGrupoId()).isEqualTo(grupoB.getId());
    }

    @Test
    void reatribuirPassaOGrupoTodoDeUmaVezEDeixaOAdminDeOrigemSozinho() {
        Usuario outroProdutor = contaNoGrupo("outro-produtor-a@iso.test", com.agroclima.api.business.auth.Papel.PRODUTOR_RURAL, grupoA);
        String tokenAntigo = token(produtorA);

        ResponseEntity<Map> resposta = reatribuir(master, grupoA, grupoB);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) dados(resposta).get("produtores")).intValue()).isEqualTo(2);
        assertThat(((Number) dados(resposta).get("agronomos")).intValue()).isEqualTo(1);
        for (Usuario produtor : List.of(produtorA, outroProdutor)) {
            assertThat(usuarioRepository.findById(produtor.getId()).orElseThrow().getGrupoId()).isEqualTo(grupoB.getId());
        }
        assertThat(participacaoRepository.findByIdAgronomoId(agronomoA.getId()))
                .extracting(ParticipacaoAgronomo::getGrupoId).containsExactly(grupoB.getId());
        assertThat(propriedadeRepository.findById(propriedadeA.getId()).orElseThrow().getGrupoId()).isEqualTo(grupoB.getId());
        assertThat(usuarioRepository.findById(adminA.getId()).orElseThrow().getGrupoId()).isEqualTo(grupoA.getId());
        // O vinculo do agronomo A segue valendo: a propriedade e ele foram para o mesmo grupo.
        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades/" + propriedadeA.getId(), atual(agronomoA), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(statusDe(HttpMethod.GET, "/api/v1/propriedades", tokenAntigo)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reatribuirAuditaCadaMovimentoEUmResumo() {
        reatribuir(master, grupoA, grupoB);

        assertThat(auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(produtorA.getId()))
                .extracting(AuditoriaConta::getAcao).containsExactly(AcaoAuditoria.MOVER_GRUPO);
        assertThat(auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(agronomoA.getId()))
                .extracting(AuditoriaConta::getAcao).containsExactly(AcaoAuditoria.MOVER_GRUPO);
        assertThat(auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(adminA.getId())).singleElement().satisfies(a -> {
            assertThat(a.getAcao()).isEqualTo(AcaoAuditoria.REATRIBUIR_GRUPO);
            assertThat(a.getAlvoGrupoId()).isEqualTo(grupoA.getId());
            assertThat(a.getDetalhe()).startsWith("1 produtores e 1 agrônomos");
        });
    }

    @Test
    void suspenderOAdminNaoDerrubaOGrupoEDepoisOMasterReatribui() {
        chamar(HttpMethod.POST, "/api/v1/usuarios/" + adminA.getId() + "/suspender", master, null);
        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades", produtorA, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        reatribuir(master, grupoA, grupoB);

        assertThat(usuarioRepository.findById(adminA.getId()).orElseThrow().getStatus()).isEqualTo(StatusConta.SUSPENSA);
        assertThat(usuarioRepository.findById(produtorA.getId()).orElseThrow().getGrupoId()).isEqualTo(grupoB.getId());
        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades", adminB, null).getBody().toString()).contains("Fazenda A");
    }

    @Test
    void reatribuirParaOMesmoGrupoOuParaGrupoInexistenteERecusado() {
        assertThat(reatribuir(master, grupoA, grupoA).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<Map> inexistente = chamar(HttpMethod.POST, "/api/v1/grupos/" + grupoA.getId() + "/reatribuir", master,
                Map.of("grupo_destino_id", java.util.UUID.randomUUID().toString()));
        assertThat(inexistente.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
