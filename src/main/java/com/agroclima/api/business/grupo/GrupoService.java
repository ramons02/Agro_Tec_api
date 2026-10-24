package com.agroclima.api.business.grupo;

import com.agroclima.api.business.auth.GeradorSenhaTemporaria;
import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.usuario.AcaoAuditoria;
import com.agroclima.api.business.usuario.AuditoriaConta;
import com.agroclima.api.business.usuario.AuditoriaContaRepository;
import com.agroclima.api.business.vinculo.VinculoAgronomoPropriedadeRepository;
import com.agroclima.api.core.config.AppProperties;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.security.AutorizacaoService;
import com.agroclima.api.core.security.EstadoContaCache;
import com.agroclima.api.core.security.UsuarioAutenticado;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gestao de grupos (spec 024): o Master cria Admins com o grupo deles, move usuarios e reatribui o grupo de um Admin
 * a outro; Master e Admin removem um agronomo do grupo. Toda mudanca de grupo derruba as sessoes da conta afetada
 * (versao de sessao) e e auditada. Fora do escopo do solicitante, o grupo responde como se nao existisse (404).
 */
@Service
public class GrupoService {

    static final Duration VALIDADE_SENHA_TEMPORARIA = Duration.ofDays(7);

    private final GrupoRepository grupoRepository;
    private final ParticipacaoAgronomoRepository participacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PropriedadeRepository propriedadeRepository;
    private final VinculoAgronomoPropriedadeRepository vinculoRepository;
    private final AuditoriaContaRepository auditoriaRepository;
    private final AutorizacaoService autorizacaoService;
    private final EstadoContaCache estadoContaCache;
    private final PasswordEncoder passwordEncoder;
    private final GeradorSenhaTemporaria geradorSenha;
    private final Clock clock;
    private final AppProperties appProperties;
    private final SecureRandom random = new SecureRandom();

    public GrupoService(
            GrupoRepository grupoRepository,
            ParticipacaoAgronomoRepository participacaoRepository,
            UsuarioRepository usuarioRepository,
            PropriedadeRepository propriedadeRepository,
            VinculoAgronomoPropriedadeRepository vinculoRepository,
            AuditoriaContaRepository auditoriaRepository,
            AutorizacaoService autorizacaoService,
            EstadoContaCache estadoContaCache,
            PasswordEncoder passwordEncoder,
            GeradorSenhaTemporaria geradorSenha,
            Clock clock,
            AppProperties appProperties) {
        this.appProperties = appProperties;
        this.grupoRepository = grupoRepository;
        this.participacaoRepository = participacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.propriedadeRepository = propriedadeRepository;
        this.vinculoRepository = vinculoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.autorizacaoService = autorizacaoService;
        this.estadoContaCache = estadoContaCache;
        this.passwordEncoder = passwordEncoder;
        this.geradorSenha = geradorSenha;
        this.clock = clock;
    }

    public record AdminResumo(UUID id, String nome, String email) {}

    public record ItemGrupo(UUID id, String nome, AdminResumo admin, long usuarios, Instant criadoEm) {}

    /** Resposta de POST /grupos: a senha temporaria do Admin aparece so aqui, uma unica vez. */
    public record GrupoCriado(
            UUID id, String nome, AdminResumo admin, String senhaTemporaria, Instant senhaTemporariaExpiraEm) {}

    public record ResultadoReatribuicao(int produtores, int agronomos) {}

    /** Resposta de POST /grupos/{id}/novo-link: o link aparece so aqui; depois so existe o hash. */
    public record NovoLink(String link, Instant geradoEm) {}

    /** Master: todos os grupos; Admin: o dele. */
    @Transactional(readOnly = true)
    public List<ItemGrupo> listar(UsuarioAutenticado solicitante) {
        autorizacaoService.exigirGestor(solicitante);
        List<Grupo> grupos;
        if (autorizacaoService.ehMaster(solicitante)) {
            grupos = grupoRepository.findAll(org.springframework.data.domain.Sort.by("criadoEm", "id"));
        } else {
            grupos = autorizacaoService.grupoDe(solicitante).flatMap(grupoRepository::findById).stream().toList();
        }
        Map<UUID, Usuario> admins = usuarioRepository
                .findAllById(grupos.stream().map(Grupo::getAdminId).distinct().toList()).stream()
                .collect(Collectors.toMap(Usuario::getId, u -> u));
        List<ItemGrupo> itens = new ArrayList<>();
        for (Grupo grupo : grupos) {
            Usuario admin = admins.get(grupo.getAdminId());
            itens.add(new ItemGrupo(
                    grupo.getId(),
                    grupo.getNome(),
                    admin == null ? null : new AdminResumo(admin.getId(), admin.getNome(), admin.getEmail()),
                    usuariosDoGrupo(grupo.getId()),
                    grupo.getCriadoEm()));
        }
        return itens;
    }

    /** Cria o Admin (senha temporaria, troca obrigatoria) e o grupo dele. So o Master. */
    @Transactional
    public GrupoCriado criar(UsuarioAutenticado solicitante, String nome, String adminNome, String adminEmail) {
        autorizacaoService.exigirMaster(solicitante);
        String nomeGrupo = nome.trim();
        String emailLimpo = adminEmail.trim();
        if (grupoRepository.existsByNomeIgnoreCase(nomeGrupo)) {
            throw new AppException(409, "Já existe um grupo com esse nome.");
        }
        if (usuarioRepository.existsByEmailIgnoreCase(emailLimpo)) {
            throw new AppException(409, "Email já cadastrado.");
        }
        String senha = geradorSenha.gerar();
        Instant agora = clock.instant();
        Instant expiraEm = agora.plus(VALIDADE_SENHA_TEMPORARIA);
        Usuario admin = usuarioRepository.save(Usuario.criadaPeloGestor(
                adminNome.trim(), emailLimpo, Papel.GESTOR_TECNOLOGIA, passwordEncoder.encode(senha), agora, expiraEm));
        // O link de cadastro nasce desligado: o codigo e aleatorio e desconhecido ate o Admin gerar um (fase 4).
        Grupo grupo = grupoRepository.save(new Grupo(nomeGrupo, admin.getId(), hashDe(codigoAleatorio()), agora));
        admin.definirGrupo(grupo.getId());
        usuarioRepository.save(admin);
        auditoriaRepository.save(new AuditoriaConta(
                        solicitante.id(), AcaoAuditoria.CRIAR_ADMIN, admin.getId(), admin.getEmail(),
                        "grupo " + nomeGrupo, agora)
                .comGrupo(grupo.getId()));
        return new GrupoCriado(
                grupo.getId(), grupo.getNome(),
                new AdminResumo(admin.getId(), admin.getNome(), admin.getEmail()), senha, expiraEm);
    }

    /**
     * Move um produtor (com as propriedades e talhoes dele) ou um agronomo para outro grupo. Agronomo: troca a
     * participacao de origem pela de destino (a origem so pode ser omitida se ele tiver uma so). Admin e Master nao
     * se movem por aqui.
     */
    @Transactional
    public void mover(UsuarioAutenticado solicitante, UUID usuarioId, UUID grupoDestinoId, UUID grupoOrigemId) {
        autorizacaoService.exigirMaster(solicitante);
        Usuario usuario = usuarioRepository.buscarParaAtualizar(usuarioId)
                .orElseThrow(() -> new AppException(404, "Usuário não encontrado."));
        Grupo destino = grupoRepository.findById(grupoDestinoId)
                .orElseThrow(() -> new AppException(404, "Grupo não encontrado."));
        Instant agora = clock.instant();
        String detalhe;
        if (usuario.getPapel() == Papel.PRODUTOR_RURAL) {
            if (destino.getId().equals(usuario.getGrupoId())) {
                throw new AppException(409, "O usuário já está nesse grupo.");
            }
            detalhe = "de " + nomeDoGrupo(usuario.getGrupoId()) + " para " + destino.getNome();
            moverProdutor(usuario, destino.getId());
        } else if (usuario.getPapel() == Papel.AGRONOMO) {
            UUID origem = origemDoAgronomo(usuario, grupoOrigemId);
            if (origem.equals(destino.getId())) {
                throw new AppException(409, "O usuário já está nesse grupo.");
            }
            detalhe = "de " + nomeDoGrupo(origem) + " para " + destino.getNome();
            moverAgronomo(usuario, origem, destino.getId(), agora);
        } else {
            throw new AppException(409, "Admins e Masters não mudam de grupo por aqui.");
        }
        usuario.derrubarSessoes();
        usuarioRepository.save(usuario);
        estadoContaCache.invalidar(usuario.getId());
        auditoriaRepository.save(new AuditoriaConta(
                        solicitante.id(), AcaoAuditoria.MOVER_GRUPO, usuario.getId(), usuario.getEmail(), detalhe, agora)
                .comGrupo(destino.getId()));
    }

    /**
     * Passa todos os produtores e agronomos de um grupo (um Admin que saiu ou foi suspenso) para outro Admin, de uma
     * vez. Propriedades e talhoes acompanham; o Admin de origem fica sozinho no grupo dele.
     */
    @Transactional
    public ResultadoReatribuicao reatribuir(UsuarioAutenticado solicitante, UUID grupoOrigemId, UUID grupoDestinoId) {
        autorizacaoService.exigirMaster(solicitante);
        Grupo origem = grupoRepository.findById(grupoOrigemId)
                .orElseThrow(() -> new AppException(404, "Grupo não encontrado."));
        Grupo destino = grupoRepository.findById(grupoDestinoId)
                .orElseThrow(() -> new AppException(404, "Grupo não encontrado."));
        if (origem.getId().equals(destino.getId())) {
            throw new AppException(409, "Origem e destino são o mesmo grupo.");
        }
        Instant agora = clock.instant();
        String detalhe = "reatribuição de " + origem.getNome() + " para " + destino.getNome();
        int produtores = 0;
        for (Usuario produtor : usuarioRepository.findByGrupoIdAndPapel(origem.getId(), Papel.PRODUTOR_RURAL)) {
            moverProdutor(produtor, destino.getId());
            produtor.derrubarSessoes();
            usuarioRepository.save(produtor);
            estadoContaCache.invalidar(produtor.getId());
            auditoriaRepository.save(new AuditoriaConta(solicitante.id(), AcaoAuditoria.MOVER_GRUPO, produtor.getId(),
                            produtor.getEmail(), detalhe, agora)
                    .comGrupo(destino.getId()));
            produtores++;
        }
        int agronomos = 0;
        for (ParticipacaoAgronomo participacao : participacaoRepository.findByIdGrupoId(origem.getId())) {
            Usuario agronomo = usuarioRepository.findById(participacao.getAgronomoId()).orElse(null);
            if (agronomo == null) {
                continue;
            }
            moverAgronomo(agronomo, origem.getId(), destino.getId(), agora);
            agronomo.derrubarSessoes();
            usuarioRepository.save(agronomo);
            estadoContaCache.invalidar(agronomo.getId());
            auditoriaRepository.save(new AuditoriaConta(solicitante.id(), AcaoAuditoria.MOVER_GRUPO, agronomo.getId(),
                            agronomo.getEmail(), detalhe, agora)
                    .comGrupo(destino.getId()));
            agronomos++;
        }
        String emailAdmin = usuarioRepository.findById(origem.getAdminId()).map(Usuario::getEmail).orElse("");
        auditoriaRepository.save(new AuditoriaConta(solicitante.id(), AcaoAuditoria.REATRIBUIR_GRUPO, origem.getAdminId(),
                        emailAdmin, produtores + " produtores e " + agronomos + " agrônomos: " + detalhe, agora)
                .comGrupo(origem.getId()));
        return new ResultadoReatribuicao(produtores, agronomos);
    }

    /**
     * Gera um novo link de cadastro do grupo: o anterior para de valer na hora (so o hash do codigo atual existe).
     * Master (qualquer grupo) ou Admin do grupo; outro Admin recebe 404, como se o grupo nao existisse.
     */
    @Transactional
    public NovoLink gerarNovoLink(UsuarioAutenticado solicitante, UUID grupoId) {
        autorizacaoService.exigirGestor(solicitante);
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new AppException(404, "Grupo não encontrado."));
        if (!autorizacaoService.ehMaster(solicitante)
                && !autorizacaoService.grupoDe(solicitante).map(g -> g.equals(grupo.getId())).orElse(false)) {
            throw new AppException(404, "Grupo não encontrado.");
        }
        String codigo = codigoAleatorio();
        Instant agora = clock.instant();
        grupo.renovarCodigoCadastro(hashDe(codigo), agora);
        grupoRepository.save(grupo);
        String emailAdmin = usuarioRepository.findById(grupo.getAdminId()).map(Usuario::getEmail).orElse("");
        auditoriaRepository.save(new AuditoriaConta(solicitante.id(), AcaoAuditoria.NOVO_LINK_GRUPO, grupo.getAdminId(),
                        emailAdmin, "novo link de cadastro de " + grupo.getNome(), agora)
                .comGrupo(grupo.getId()));
        return new NovoLink(appProperties.frontendBaseUrl() + "/#/cadastro?grupo=" + codigo, agora);
    }

    /** Grupo do link de cadastro, ou 404 para link desconhecido ou substituido (nao revela qual dos dois). */
    @Transactional(readOnly = true)
    public UUID resolverLink(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new AppException(404, "Link de cadastro inválido ou substituído.");
        }
        return grupoRepository.findByCodigoCadastroHash(hashDe(codigo.trim()))
                .map(Grupo::getId)
                .orElseThrow(() -> new AppException(404, "Link de cadastro inválido ou substituído."));
    }

    /** Tira o agronomo do grupo e encerra os vinculos dele com as propriedades do grupo. Master ou Admin do grupo. */
    @Transactional
    public void removerAgronomo(UsuarioAutenticado solicitante, UUID grupoId, UUID agronomoId) {
        autorizacaoService.exigirGestor(solicitante);
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new AppException(404, "Grupo não encontrado."));
        if (!autorizacaoService.ehMaster(solicitante)
                && !autorizacaoService.grupoDe(solicitante).map(g -> g.equals(grupo.getId())).orElse(false)) {
            throw new AppException(404, "Grupo não encontrado.");
        }
        Usuario agronomo = usuarioRepository.buscarParaAtualizar(agronomoId)
                .orElseThrow(() -> new AppException(404, "Usuário não encontrado."));
        ParticipacaoAgronomo.Id chave = new ParticipacaoAgronomo.Id(agronomoId, grupo.getId());
        if (agronomo.getPapel() != Papel.AGRONOMO || !participacaoRepository.existsById(chave)) {
            throw new AppException(404, "Agrônomo não encontrado neste grupo.");
        }
        if (participacaoRepository.countByIdAgronomoId(agronomoId) <= 1) {
            throw new AppException(409,
                    "O agrônomo precisa estar em ao menos um grupo; para tirá-lo de vez, peça ao administrador da plataforma.");
        }
        Instant agora = clock.instant();
        participacaoRepository.deleteById(chave);
        int vinculos = vinculoRepository.apagarDoAgronomoNoGrupo(agronomoId, grupo.getId());
        agronomo.derrubarSessoes();
        usuarioRepository.save(agronomo);
        estadoContaCache.invalidar(agronomoId);
        auditoriaRepository.save(new AuditoriaConta(solicitante.id(), AcaoAuditoria.REMOVER_DO_GRUPO, agronomoId,
                        agronomo.getEmail(), "saiu de " + grupo.getNome() + " (" + vinculos + " vínculos encerrados)", agora)
                .comGrupo(grupo.getId()));
    }

    // ---------- apoio ----------

    private void moverProdutor(Usuario produtor, UUID destino) {
        produtor.definirGrupo(destino);
        usuarioRepository.save(produtor);
        propriedadeRepository.atualizarGrupoDoDono(produtor.getId(), destino);
    }

    private void moverAgronomo(Usuario agronomo, UUID origem, UUID destino, Instant agora) {
        // Sai da origem: os vinculos com propriedades dela deixam de existir; entra no destino se ainda nao esta.
        vinculoRepository.apagarDoAgronomoNoGrupo(agronomo.getId(), origem);
        participacaoRepository.deleteById(new ParticipacaoAgronomo.Id(agronomo.getId(), origem));
        ParticipacaoAgronomo.Id novo = new ParticipacaoAgronomo.Id(agronomo.getId(), destino);
        if (!participacaoRepository.existsById(novo)) {
            participacaoRepository.save(new ParticipacaoAgronomo(agronomo.getId(), destino, agora));
        }
    }

    private UUID origemDoAgronomo(Usuario agronomo, UUID origemPedida) {
        List<UUID> grupos = participacaoRepository.findByIdAgronomoId(agronomo.getId()).stream()
                .map(ParticipacaoAgronomo::getGrupoId)
                .toList();
        if (origemPedida != null) {
            if (!grupos.contains(origemPedida)) {
                throw new AppException(404, "O agrônomo não pertence ao grupo de origem informado.");
            }
            return origemPedida;
        }
        if (grupos.size() != 1) {
            throw new AppException(422, "Informe o grupo de origem: o agrônomo está em " + grupos.size() + " grupos.");
        }
        return grupos.get(0);
    }

    private long usuariosDoGrupo(UUID grupoId) {
        return usuarioRepository.countByGrupoId(grupoId) + participacaoRepository.countByIdGrupoId(grupoId);
    }

    private String nomeDoGrupo(UUID grupoId) {
        return grupoId == null ? "sem grupo" : grupoRepository.findById(grupoId).map(Grupo::getNome).orElse("sem grupo");
    }

    private String codigoAleatorio() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashDe(String codigo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
