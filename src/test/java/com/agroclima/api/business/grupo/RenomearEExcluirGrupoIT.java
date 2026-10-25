package com.agroclima.api.business.grupo;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.StatusConta;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.usuario.AcaoAuditoria;
import com.agroclima.api.business.usuario.AuditoriaContaRepository;
import com.agroclima.api.support.GruposDeTeste;
import com.agroclima.api.isolamento.IsolamentoBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** O Master renomeia e exclui grupos; grupo com contas ou propriedades nao e excluido. */
class RenomearEExcluirGrupoIT extends IsolamentoBase {

    @Autowired
    private AuditoriaContaRepository auditoriaRepository;

    private ResponseEntity<Map> renomear(Usuario como, Grupo grupo, String nome) {
        return chamar(HttpMethod.PATCH, "/api/v1/grupos/" + grupo.getId(), como, Map.of("nome", nome));
    }

    private ResponseEntity<Map> excluir(Usuario como, Grupo grupo) {
        return chamar(HttpMethod.DELETE, "/api/v1/grupos/" + grupo.getId(), como, null);
    }

    private Grupo grupoVazio(String nome) {
        Usuario admin = conta(nome.toLowerCase().replace(' ', '-') + "@iso.test", Papel.GESTOR_TECNOLOGIA);
        return GruposDeTeste.criarGrupo(grupoRepository, usuarioRepository, nome, admin);
    }

    @Test
    void masterRenomeiaEAuditaEOutroGrupoNaoPodeTerOMesmoNome() {
        ResponseEntity<Map> resposta = renomear(master, grupoA, "Cooperativa Nova");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(grupoRepository.findById(grupoA.getId()).orElseThrow().getNome()).isEqualTo("Cooperativa Nova");
        assertThat(auditoriaRepository.findAll()).extracting(a -> a.getAcao()).contains(AcaoAuditoria.RENOMEAR_GRUPO);
        assertThat(renomear(master, grupoA, "grupo b").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(renomear(master, grupoA, "COOPERATIVA NOVA").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void soOMasterRenomeiaEExclui() {
        Grupo vazio = grupoVazio("Grupo Vazio");
        for (Usuario usuario : List.of(adminA, produtorA, agronomoA)) {
            assertThat(renomear(usuario, grupoA, "X").getStatusCode()).as("renomear como %s", usuario.getEmail())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(excluir(usuario, vazio).getStatusCode()).as("excluir como %s", usuario.getEmail())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(grupoRepository.existsById(vazio.getId())).isTrue();
    }

    @Test
    void grupoInexistenteResponde404() {
        Grupo fantasma = new Grupo("Fantasma", master.getId(), "f".repeat(64), java.time.Instant.now());
        assertThat(renomear(master, fantasma, "Qualquer").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(excluir(master, fantasma).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void grupoComContasOuPropriedadesNaoEExcluido() {
        assertThat(excluir(master, grupoA).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(grupoRepository.existsById(grupoA.getId())).isTrue();
        assertThat(usuarioRepository.findById(produtorA.getId()).orElseThrow().getGrupoId()).isEqualTo(grupoA.getId());
    }

    @Test
    void grupoVazioEExcluidoEOAdminFicaSemGrupoESuspenso() {
        Grupo vazio = grupoVazio("Grupo Vazio");
        Usuario admin = usuarioRepository.findById(vazio.getAdminId()).orElseThrow();

        ResponseEntity<Map> resposta = excluir(master, vazio);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(grupoRepository.existsById(vazio.getId())).isFalse();
        Usuario depois = usuarioRepository.findById(admin.getId()).orElseThrow();
        assertThat(depois.getGrupoId()).isNull();
        assertThat(depois.getStatus()).isEqualTo(StatusConta.SUSPENSA);
        assertThat(auditoriaRepository.findAll()).extracting(a -> a.getAcao()).contains(AcaoAuditoria.EXCLUIR_GRUPO);
    }

    @Test
    void depoisDeReatribuirOGrupoPodeSerExcluidoENuncaOUltimo() {
        assertThat(chamar(HttpMethod.POST, "/api/v1/grupos/" + grupoA.getId() + "/reatribuir", master,
                Map.of("grupo_destino_id", grupoB.getId().toString())).getStatusCode()).isEqualTo(HttpStatus.OK);
        propriedadeRepository.findAll().stream().filter(p -> grupoA.getId().equals(p.getGrupoId()))
                .forEach(propriedadeRepository::delete);

        assertThat(excluir(master, grupoA).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(excluir(master, grupoB).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
