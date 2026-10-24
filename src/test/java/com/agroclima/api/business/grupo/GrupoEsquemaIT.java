package com.agroclima.api.business.grupo;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.propriedade.PropriedadeService;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.core.security.UsuarioAutenticado;
import com.agroclima.api.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 024, fase 1: o esquema novo funciona pelo JPA e a propriedade nasce no grupo do dono. */
class GrupoEsquemaIT extends IntegrationTestBase {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private GrupoRepository grupoRepository;

    @Autowired
    private ParticipacaoAgronomoRepository participacaoRepository;

    @Autowired
    private PropriedadeService propriedadeService;

    @Autowired
    private PropriedadeRepository propriedadeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private Usuario conta(String email, Papel papel) {
        return usuarioRepository.save(new Usuario("Conta", email, passwordEncoder.encode("senha12345"), papel));
    }

    private Grupo grupo(String nome, UUID adminId, char codigo) {
        return grupoRepository.save(new Grupo(nome, adminId, String.valueOf(codigo).repeat(64), Instant.now()));
    }

    @Test
    void grupoEParticipacaoSaoGravadosELidosPeloJpa() {
        Usuario admin = conta("admin@teste.com", Papel.GESTOR_TECNOLOGIA);
        Grupo grupo = grupo("Cliente Um", admin.getId(), 'a');
        Usuario agronomo = conta("agro@teste.com", Papel.AGRONOMO);
        participacaoRepository.save(new ParticipacaoAgronomo(agronomo.getId(), grupo.getId(), Instant.now()));

        assertThat(grupoRepository.findByAdminId(admin.getId())).get().extracting(Grupo::getNome).isEqualTo("Cliente Um");
        assertThat(grupoRepository.findByCodigoCadastroHash("a".repeat(64))).isPresent();
        assertThat(participacaoRepository.findByIdAgronomoId(agronomo.getId())).singleElement()
                .extracting(ParticipacaoAgronomo::getGrupoId).isEqualTo(grupo.getId());
        assertThat(participacaoRepository.countByIdAgronomoId(agronomo.getId())).isEqualTo(1);
    }

    @Test
    void nomeDeGrupoEUnicoSemDiferenciarMaiusculasECodigoDoLinkTambem() {
        Usuario admin = conta("admin2@teste.com", Papel.GESTOR_TECNOLOGIA);
        grupo("Cliente Dois", admin.getId(), 'b');

        assertThatThrownBy(() -> grupoRepository.saveAndFlush(
                new Grupo("CLIENTE DOIS", admin.getId(), "c".repeat(64), Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> grupoRepository.saveAndFlush(
                new Grupo("Outro nome", admin.getId(), "b".repeat(64), Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void participacaoNaoSeRepeteParaOMesmoAgronomoEGrupo() {
        Usuario admin = conta("admin3@teste.com", Papel.GESTOR_TECNOLOGIA);
        Grupo grupo = grupo("Cliente Tres", admin.getId(), 'd');
        Usuario agronomo = conta("agro2@teste.com", Papel.AGRONOMO);
        participacaoRepository.saveAndFlush(new ParticipacaoAgronomo(agronomo.getId(), grupo.getId(), Instant.now()));

        // Gravar de novo a mesma chave atualiza a mesma linha: continua uma so participacao.
        participacaoRepository.saveAndFlush(new ParticipacaoAgronomo(agronomo.getId(), grupo.getId(), Instant.now()));

        assertThat(participacaoRepository.countByIdAgronomoId(agronomo.getId())).isEqualTo(1);
    }

    @Test
    void propriedadeNasceNoGrupoDoDono() throws Exception {
        Usuario admin = conta("admin4@teste.com", Papel.GESTOR_TECNOLOGIA);
        Grupo grupo = grupo("Cliente Quatro", admin.getId(), 'e');
        Usuario produtor = conta("prod@teste.com", Papel.PRODUTOR_RURAL);
        produtor.definirGrupo(grupo.getId());
        usuarioRepository.save(produtor);
        var geometria = objectMapper.readTree("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[-48.5,-1.45],[-48.49,-1.45],"
                + "[-48.49,-1.44],[-48.5,-1.44],[-48.5,-1.45]]]]}");

        var criada = propriedadeService.criar(new UsuarioAutenticado(produtor.getId(), Papel.PRODUTOR_RURAL),
                "Fazenda do Grupo", "Belem", geometria);

        assertThat(propriedadeRepository.findById(criada.getId()).orElseThrow().getGrupoId()).isEqualTo(grupo.getId());
    }

    @Test
    void propriedadeDeDonoSemGrupoFicaSemGrupoSemQuebrar() throws Exception {
        Usuario produtor = conta("semgrupo@teste.com", Papel.PRODUTOR_RURAL);
        var geometria = objectMapper.readTree("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[-48.5,-1.45],[-48.49,-1.45],"
                + "[-48.49,-1.44],[-48.5,-1.44],[-48.5,-1.45]]]]}");

        var criada = propriedadeService.criar(new UsuarioAutenticado(produtor.getId(), Papel.PRODUTOR_RURAL),
                "Fazenda Sem Grupo", "Belem", geometria);

        assertThat(propriedadeRepository.findById(criada.getId()).orElseThrow().getGrupoId()).isNull();
    }
}
