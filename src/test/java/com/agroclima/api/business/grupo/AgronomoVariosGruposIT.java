package com.agroclima.api.business.grupo;

import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.usuario.AcaoAuditoria;
import com.agroclima.api.business.usuario.AuditoriaConta;
import com.agroclima.api.business.usuario.AuditoriaContaRepository;
import com.agroclima.api.business.vinculo.VinculoAgronomoPropriedade;
import com.agroclima.api.isolamento.IsolamentoBase;
import org.junit.jupiter.api.BeforeEach;
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

/** US5 (spec 024): agronomo em dois grupos; o Admin so o remove do grupo dele; nunca fica sem grupo. */
class AgronomoVariosGruposIT extends IsolamentoBase {

    @Autowired
    private AuditoriaContaRepository auditoriaRepository;

    private Usuario agronomo;

    @BeforeEach
    void agronomoNosDoisGrupos() {
        agronomo = agronomoA;
        participacaoRepository.save(new ParticipacaoAgronomo(agronomo.getId(), grupoB.getId(), Instant.now()));
        VinculoAgronomoPropriedade vinculo = new VinculoAgronomoPropriedade(agronomo.getId(), propriedadeB.getId());
        vinculo.aceitar(Instant.now());
        vinculoRepository.save(vinculo);
    }

    private ResponseEntity<Map> remover(Usuario como, Grupo grupo, Usuario alvo) {
        return chamar(HttpMethod.DELETE, "/api/v1/grupos/" + grupo.getId() + "/agronomos/" + alvo.getId(), como, null);
    }

    private HttpStatus statusDe(String caminho, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return HttpStatus.valueOf(restTemplate.exchange(caminho, HttpMethod.GET, new HttpEntity<>(headers), Map.class)
                .getStatusCode().value());
    }

    @Test
    void veAsPropriedadesVinculadasDeCadaGrupoENenhumaOutra() {
        Propriedade semVinculo = new Propriedade("Fazenda sem vinculo", "Belém", produtorB.getId(), null);
        semVinculo.definirGrupo(grupoB.getId());
        semVinculo = propriedadeRepository.save(semVinculo);

        String lista = chamar(HttpMethod.GET, "/api/v1/propriedades", agronomo, null).getBody().toString();

        assertThat(lista).contains("Fazenda A").contains("Fazenda B").doesNotContain("Fazenda sem vinculo");
        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades/" + semVinculo.getId(), agronomo, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void oAdminRemoveSoDoGrupoDeleEEncerraOsVinculosDesseGrupo() {
        String tokenAntigo = token(agronomo);

        ResponseEntity<Map> resposta = remover(adminA, grupoA, agronomo);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(participacaoRepository.findByIdAgronomoId(agronomo.getId()))
                .extracting(ParticipacaoAgronomo::getGrupoId).containsExactly(grupoB.getId());
        assertThat(vinculoRepository.findAll()).extracting(VinculoAgronomoPropriedade::getPropriedadeId)
                .doesNotContain(propriedadeA.getId()).contains(propriedadeB.getId());
        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades/" + propriedadeA.getId(), atual(agronomo), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chamar(HttpMethod.GET, "/api/v1/propriedades/" + propriedadeB.getId(), atual(agronomo), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(statusDe("/api/v1/propriedades", tokenAntigo)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void masterTambemRemoveEAuditoriaGuardaOGrupo() {
        remover(master, grupoB, agronomo);

        List<AuditoriaConta> trilha = auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(agronomo.getId());

        assertThat(trilha).singleElement().satisfies(a -> {
            assertThat(a.getAcao()).isEqualTo(AcaoAuditoria.REMOVER_DO_GRUPO);
            assertThat(a.getAtorId()).isEqualTo(master.getId());
            assertThat(a.getAlvoGrupoId()).isEqualTo(grupoB.getId());
        });
    }

    @Test
    void agronomoNuncaFicaSemGrupo() {
        remover(adminA, grupoA, agronomo);

        ResponseEntity<Map> ultimo = remover(adminB, grupoB, agronomo);

        assertThat(ultimo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(participacaoRepository.countByIdAgronomoId(agronomo.getId())).isEqualTo(1);
    }

    @Test
    void adminNaoRemoveDeOutroGrupoNemQuemNaoEstaNoGrupo() {
        assertThat(remover(adminB, grupoA, agronomo).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(remover(adminA, grupoA, agronomoB).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(remover(adminA, grupoA, produtorA).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(participacaoRepository.countByIdAgronomoId(agronomo.getId())).isEqualTo(2);
    }

    @Test
    void produtorEAgronomoNaoRemovemNinguemDeGrupo() {
        for (Usuario usuario : List.of(produtorA, agronomoB)) {
            assertThat(remover(usuario, grupoA, agronomo).getStatusCode()).as(usuario.getEmail()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(participacaoRepository.countByIdAgronomoId(agronomo.getId())).isEqualTo(2);
    }

    @Test
    void oAdminNaoSuspendeNemRedefineASenhaDoAgronomoDeDoisGrupos() {
        String base = "/api/v1/usuarios/" + agronomo.getId();

        assertThat(chamar(HttpMethod.POST, base + "/suspender", adminA, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.POST, base + "/redefinir-senha", adminB, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.POST, base + "/suspender", master, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
