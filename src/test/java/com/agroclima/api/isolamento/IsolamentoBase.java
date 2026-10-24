package com.agroclima.api.isolamento;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.grupo.Grupo;
import com.agroclima.api.business.grupo.GrupoRepository;
import com.agroclima.api.business.grupo.ParticipacaoAgronomo;
import com.agroclima.api.business.grupo.ParticipacaoAgronomoRepository;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.talhao.Talhao;
import com.agroclima.api.business.talhao.TalhaoRepository;
import com.agroclima.api.business.vinculo.VinculoAgronomoPropriedade;
import com.agroclima.api.business.vinculo.VinculoAgronomoPropriedadeRepository;
import com.agroclima.api.core.geo.GeometriaUtil;
import com.agroclima.api.core.security.JwtService;
import com.agroclima.api.support.GruposDeTeste;
import com.agroclima.api.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Cenario da suite de isolamento (spec 024, FR-018): dois grupos, A e B, cada um com Admin, produtor, agronomo,
 * propriedade, talhao e vinculo, mais o Master. As rotas de dados e de gestao ficam listadas aqui; o teste de
 * cobertura falha se aparecer uma rota nova que nao esteja em nenhuma das tres listas.
 */
public abstract class IsolamentoBase extends IntegrationTestBase {

    /** Rotas de dados, exercitadas por IsolamentoDadosIT. */
    public static final Set<String> ROTAS_DE_DADOS = Set.of(
            "GET /api/v1/propriedades",
            "POST /api/v1/propriedades",
            "GET /api/v1/propriedades/{id}",
            "PUT /api/v1/propriedades/{id}",
            "DELETE /api/v1/propriedades/{id}",
            "GET /api/v1/talhoes",
            "POST /api/v1/talhoes",
            "POST /api/v1/talhoes/importar",
            "GET /api/v1/talhoes/{id}",
            "DELETE /api/v1/talhoes/{id}",
            "GET /api/v1/talhoes/{id}/pulverizacao",
            "GET /api/v1/talhoes/{id}/recomendacao",
            "GET /api/v1/talhoes/{id}/balanco-hidrico",
            "GET /api/v1/talhoes/{id}/balanco-hidrico/historico",
            "GET /api/v1/mapa/dados",
            "GET /api/v1/dashboard/plantio",
            "GET /api/v1/dashboard/plantio/exportar.csv",
            "GET /api/v1/clima/atual",
            "POST /api/v1/propriedades/{propriedadeId}/vinculos");

    /** Rotas de gestao de contas, exercitadas por IsolamentoContasIT. */
    public static final Set<String> ROTAS_DE_GESTAO = Set.of(
            "GET /api/v1/usuarios",
            "GET /api/v1/usuarios/resumo",
            "GET /api/v1/usuarios/{id}",
            "POST /api/v1/usuarios",
            "POST /api/v1/usuarios/{id}/confirmar",
            "DELETE /api/v1/usuarios/{id}",
            "POST /api/v1/usuarios/{id}/suspender",
            "POST /api/v1/usuarios/{id}/reativar",
            "POST /api/v1/usuarios/{id}/redefinir-senha",
            "PATCH /api/v1/usuarios/{id}",
            "POST /api/v1/auth/convites-master",
            "GET /api/v1/grupos",
            "POST /api/v1/grupos",
            "POST /api/v1/usuarios/{id}/mover",
            "POST /api/v1/grupos/{id}/reatribuir",
            "DELETE /api/v1/grupos/{id}/agronomos/{agronomoId}");

    /** Rotas que, por desenho, nao tem escopo de grupo: publicas, da propria conta ou sem dado de ninguem. */
    public static final Set<String> ROTAS_SEM_ESCOPO_DE_GRUPO = Set.of(
            "GET /health",
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/registro",
            "GET /api/v1/auth/me",
            "POST /api/v1/auth/trocar-senha",
            "POST /api/v1/auth/recuperar-senha",
            "POST /api/v1/auth/redefinir-senha",
            "GET /api/v1/previsao",
            "POST /api/v1/telegram/vincular-link",
            "POST /api/v1/telegram/webhook",
            "DELETE /api/v1/telegram/vinculo",
            // O vinculo e aceito so pelo agronomo convidado (confere o proprio id), nao por escopo de grupo.
            "POST /api/v1/vinculos/{vinculoId}/aceitar");

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    protected GrupoRepository grupoRepository;

    @Autowired
    protected ParticipacaoAgronomoRepository participacaoRepository;

    @Autowired
    protected PropriedadeRepository propriedadeRepository;

    @Autowired
    protected TalhaoRepository talhaoRepository;

    @Autowired
    protected VinculoAgronomoPropriedadeRepository vinculoRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    protected Usuario master;
    protected Grupo grupoA;
    protected Grupo grupoB;
    protected Usuario adminA;
    protected Usuario adminB;
    protected Usuario produtorA;
    protected Usuario produtorB;
    protected Usuario agronomoA;
    protected Usuario agronomoB;
    protected Propriedade propriedadeA;
    protected Propriedade propriedadeB;
    protected Talhao talhaoA;
    protected Talhao talhaoB;

    @BeforeEach
    void montarOsDoisGrupos() {
        master = conta("master@iso.test", Papel.ADMIN_MASTER);
        adminA = conta("admin-a@iso.test", Papel.GESTOR_TECNOLOGIA);
        adminB = conta("admin-b@iso.test", Papel.GESTOR_TECNOLOGIA);
        grupoA = GruposDeTeste.criarGrupo(grupoRepository, usuarioRepository, "Grupo A", adminA);
        grupoB = GruposDeTeste.criarGrupo(grupoRepository, usuarioRepository, "Grupo B", adminB);
        produtorA = contaNoGrupo("produtor-a@iso.test", Papel.PRODUTOR_RURAL, grupoA);
        produtorB = contaNoGrupo("produtor-b@iso.test", Papel.PRODUTOR_RURAL, grupoB);
        agronomoA = conta("agro-a@iso.test", Papel.AGRONOMO);
        agronomoB = conta("agro-b@iso.test", Papel.AGRONOMO);
        participacaoRepository.save(new ParticipacaoAgronomo(agronomoA.getId(), grupoA.getId(), Instant.now()));
        participacaoRepository.save(new ParticipacaoAgronomo(agronomoB.getId(), grupoB.getId(), Instant.now()));
        propriedadeA = propriedade("Fazenda A", produtorA, grupoA, -47.90);
        propriedadeB = propriedade("Fazenda B", produtorB, grupoB, -47.50);
        talhaoA = talhao("Talhao A", propriedadeA, -47.90);
        talhaoB = talhao("Talhao B", propriedadeB, -47.50);
        vincular(agronomoA, propriedadeA);
        vincular(agronomoB, propriedadeB);
    }

    protected Usuario conta(String email, Papel papel) {
        return usuarioRepository.save(new Usuario("Conta " + email, email, passwordEncoder.encode("senha12345"), papel));
    }

    protected Usuario contaNoGrupo(String email, Papel papel, Grupo grupo) {
        Usuario usuario = new Usuario("Conta " + email, email, passwordEncoder.encode("senha12345"), papel);
        usuario.definirGrupo(grupo.getId());
        return usuarioRepository.save(usuario);
    }

    private Propriedade propriedade(String nome, Usuario dono, Grupo grupo, double lon) {
        Propriedade propriedade = new Propriedade(nome, "Belém", dono.getId(), null);
        propriedade.definirGrupo(grupo.getId());
        return propriedadeRepository.save(propriedade);
    }

    private Talhao talhao(String nome, Propriedade propriedade, double lon) {
        double lat = -1.40;
        var poligono = GeometriaUtil.FACTORY_4326.createPolygon(new Coordinate[] {
            new Coordinate(lon, lat), new Coordinate(lon + 0.01, lat), new Coordinate(lon + 0.01, lat + 0.01),
            new Coordinate(lon, lat + 0.01), new Coordinate(lon, lat)});
        return talhaoRepository.save(
                new Talhao(propriedade.getId(), nome, GeometriaUtil.normalizarParaMultiPolygon(poligono), 100.0));
    }

    private void vincular(Usuario agronomo, Propriedade propriedade) {
        VinculoAgronomoPropriedade vinculo = new VinculoAgronomoPropriedade(agronomo.getId(), propriedade.getId());
        vinculo.aceitar(Instant.now());
        vinculoRepository.save(vinculo);
    }

    /** A conta como esta no banco agora (versao de sessao atual): use depois de uma acao que derruba sessoes. */
    protected Usuario atual(Usuario usuario) {
        return usuarioRepository.findById(usuario.getId()).orElseThrow();
    }

    /** Token valido para a conta (o papel e o grupo valem pelo banco). */
    protected String token(Usuario usuario) {
        return jwtService.criarToken(usuario.getId(), usuario.getPapel(), usuario.getVersaoSessao()).token();
    }

    protected ResponseEntity<Map> chamar(HttpMethod metodo, String caminho, Usuario como, Object corpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(como));
        if (corpo != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(caminho, metodo, new HttpEntity<>(corpo, headers), Map.class);
    }

    protected ResponseEntity<String> chamarTexto(HttpMethod metodo, String caminho, Usuario como) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(como));
        return restTemplate.exchange(caminho, metodo, new HttpEntity<>(headers), String.class);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> dados(ResponseEntity<Map> resposta) {
        return (Map<String, Object>) resposta.getBody().get("dados");
    }
}
