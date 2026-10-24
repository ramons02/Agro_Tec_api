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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** US2 (spec 024): o Master cria Admins com o grupo deles; o Admin nao cria; cada um lista so o que pode. */
class CriarAdminIT extends IsolamentoBase {

    @Autowired
    private AuditoriaContaRepository auditoriaRepository;

    private ResponseEntity<Map> criar(Usuario como, String grupo, String adminNome, String adminEmail) {
        return chamar(HttpMethod.POST, "/api/v1/grupos", como,
                Map.of("nome", grupo, "admin_nome", adminNome, "admin_email", adminEmail));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> itens(ResponseEntity<Map> lista) {
        return (List<Map<String, Object>>) dados(lista).get("itens");
    }

    @Test
    void masterCriaOGrupoEOAdminComSenhaTemporariaUnica() {
        ResponseEntity<Map> resposta = criar(master, "Cliente Novo", "Nova Admin", "nova-admin@iso.test");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-store");
        String senha = (String) dados(resposta).get("senha_temporaria");
        assertThat(senha).hasSizeGreaterThanOrEqualTo(12);
        Usuario admin = usuarioRepository.findByEmail("nova-admin@iso.test").orElseThrow();
        assertThat(admin.getPapel()).isEqualTo(Papel.GESTOR_TECNOLOGIA);
        assertThat(admin.getStatus()).isEqualTo(StatusConta.ATIVA);
        assertThat(admin.isPrecisaTrocarSenha()).isTrue();
        assertThat(passwordEncoder.matches(senha, admin.getSenhaHash())).isTrue();
        Grupo grupo = grupoRepository.findByAdminId(admin.getId()).orElseThrow();
        assertThat(grupo.getNome()).isEqualTo("Cliente Novo");
        assertThat(admin.getGrupoId()).isEqualTo(grupo.getId());
    }

    @Test
    void aSenhaTemporariaNaoApareceEmConsultaPosterior() {
        String senha = (String) dados(criar(master, "Cliente Sigilo", "Sigilo", "sigilo@iso.test")).get("senha_temporaria");

        String grupos = chamar(HttpMethod.GET, "/api/v1/grupos", master, null).getBody().toString();
        String usuarios = chamar(HttpMethod.GET, "/api/v1/usuarios?page_size=100", master, null).getBody().toString();

        assertThat(grupos).doesNotContain(senha);
        assertThat(usuarios).doesNotContain(senha);
    }

    @Test
    void oNovoAdminEntraComATemporariaEVeSoOGrupoDeleVazio() {
        String senha = (String) dados(criar(master, "Cliente Vazio", "Vazia", "vazia@iso.test")).get("senha_temporaria");
        ResponseEntity<Map> login = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", "vazia@iso.test", "senha", senha), Map.class);
        assertThat(dados(login)).containsEntry("precisa_trocar_senha", true);
        Usuario admin = usuarioRepository.findByEmail("vazia@iso.test").orElseThrow();

        String propriedades = chamar(HttpMethod.GET, "/api/v1/propriedades", admin, null).getBody().toString();
        List<Map<String, Object>> contas = itens(chamar(HttpMethod.GET, "/api/v1/usuarios?page_size=100", admin, null));

        assertThat(propriedades).doesNotContain("Fazenda A").doesNotContain("Fazenda B");
        assertThat(contas).extracting(c -> c.get("email")).containsExactly("vazia@iso.test");
    }

    @Test
    void auditoriaGuardaOGrupoENuncaASenha() {
        String senha = (String) dados(criar(master, "Cliente Audit", "Audit", "audit@iso.test")).get("senha_temporaria");
        Usuario admin = usuarioRepository.findByEmail("audit@iso.test").orElseThrow();
        Grupo grupo = grupoRepository.findByAdminId(admin.getId()).orElseThrow();

        List<AuditoriaConta> trilha = auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(admin.getId());

        assertThat(trilha).singleElement().satisfies(a -> {
            assertThat(a.getAcao()).isEqualTo(AcaoAuditoria.CRIAR_ADMIN);
            assertThat(a.getAtorId()).isEqualTo(master.getId());
            assertThat(a.getAlvoGrupoId()).isEqualTo(grupo.getId());
            assertThat(String.valueOf(a.getDetalhe())).doesNotContain(senha);
        });
    }

    @Test
    void nomeDeGrupoEEmailRepetidosDao409SemCriarNada() {
        long gruposAntes = grupoRepository.count();

        ResponseEntity<Map> nome = criar(master, "grupo a", "Outro", "outro@iso.test");
        ResponseEntity<Map> email = criar(master, "Cliente Dois", "Outro", adminA.getEmail());

        assertThat(nome.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(email.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(grupoRepository.count()).isEqualTo(gruposAntes);
        assertThat(usuarioRepository.findByEmail("outro@iso.test")).isEmpty();
    }

    @Test
    void adminProdutorEAgronomoNaoCriamGrupo() {
        for (Usuario usuario : List.of(adminA, produtorA, agronomoA)) {
            ResponseEntity<Map> resposta = criar(usuario, "Invasor " + usuario.getPapel(), "X", usuario.getPapel() + "@iso.test");

            assertThat(resposta.getStatusCode()).as(usuario.getEmail()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(grupoRepository.findAll()).hasSize(2);
    }

    @Test
    void masterListaTodosOsGruposComContagemEOAdminListaSoOdele() {
        criar(master, "Cliente Terceiro", "Terceira", "terceira@iso.test");

        List<Map<String, Object>> doMaster = itens(chamar(HttpMethod.GET, "/api/v1/grupos", master, null));
        List<Map<String, Object>> doAdminA = itens(chamar(HttpMethod.GET, "/api/v1/grupos", adminA, null));

        assertThat(doMaster).extracting(g -> g.get("nome")).containsExactly("Grupo A", "Grupo B", "Cliente Terceiro");
        // Grupo A: o Admin, o produtor e o agronomo.
        assertThat(((Number) doMaster.get(0).get("usuarios")).intValue()).isEqualTo(3);
        assertThat(doAdminA).singleElement().satisfies(g -> assertThat(g.get("nome")).isEqualTo("Grupo A"));
        assertThat(chamar(HttpMethod.GET, "/api/v1/grupos", produtorA, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
