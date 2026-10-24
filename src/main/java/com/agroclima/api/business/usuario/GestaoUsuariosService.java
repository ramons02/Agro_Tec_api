package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.GeradorSenhaTemporaria;
import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.StatusConta;
import com.agroclima.api.business.auth.TokenRecuperacaoSenhaRepository;
import com.agroclima.api.business.grupo.Grupo;
import com.agroclima.api.business.grupo.GrupoRepository;
import com.agroclima.api.business.grupo.ParticipacaoAgronomo;
import com.agroclima.api.business.grupo.ParticipacaoAgronomoRepository;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.vinculo.EstadoVinculo;
import com.agroclima.api.business.vinculo.VinculoAgronomoPropriedadeRepository;
import com.agroclima.api.core.base.ContagemPorUsuario;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.security.AutorizacaoService;
import com.agroclima.api.core.security.EstadoContaCache;
import com.agroclima.api.core.security.UsuarioAutenticado;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Monitoramento de contas para o gestor de tecnologia: quem se cadastrou (produtores, agronomos e outros
 * gestores), quando, e quanto cada um ja usa o sistema. So leitura; nunca devolve hash de senha.
 */
@Service
public class GestaoUsuariosService {

    static final int TAMANHO_PAGINA_MAXIMO = 100;

    private final UsuarioRepository usuarioRepository;
    private final PropriedadeRepository propriedadeRepository;
    private final VinculoAgronomoPropriedadeRepository vinculoRepository;
    private final AutorizacaoService autorizacaoService;
    private final AuditoriaContaRepository auditoriaRepository;
    private final TokenRecuperacaoSenhaRepository tokenRecuperacaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final GeradorSenhaTemporaria geradorSenha;
    private final EstadoContaCache estadoContaCache;
    private final Clock clock;
    private final GrupoRepository grupoRepository;
    private final ParticipacaoAgronomoRepository participacaoRepository;

    /** Validade da senha temporaria sem uso (clarificacao 1 da spec 022). */
    static final Duration VALIDADE_SENHA_TEMPORARIA = Duration.ofDays(7);

    public GestaoUsuariosService(
            UsuarioRepository usuarioRepository,
            PropriedadeRepository propriedadeRepository,
            VinculoAgronomoPropriedadeRepository vinculoRepository,
            AutorizacaoService autorizacaoService,
            AuditoriaContaRepository auditoriaRepository,
            TokenRecuperacaoSenhaRepository tokenRecuperacaoRepository,
            PasswordEncoder passwordEncoder,
            GeradorSenhaTemporaria geradorSenha,
            EstadoContaCache estadoContaCache,
            Clock clock,
            GrupoRepository grupoRepository,
            ParticipacaoAgronomoRepository participacaoRepository) {
        this.grupoRepository = grupoRepository;
        this.participacaoRepository = participacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.propriedadeRepository = propriedadeRepository;
        this.vinculoRepository = vinculoRepository;
        this.autorizacaoService = autorizacaoService;
        this.auditoriaRepository = auditoriaRepository;
        this.tokenRecuperacaoRepository = tokenRecuperacaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.geradorSenha = geradorSenha;
        this.estadoContaCache = estadoContaCache;
        this.clock = clock;
    }

    /** propriedades: produtor = as que ele possui; agronomo = as com vinculo aceito; gestor = 0. */
    public record ItemUsuario(
            UUID id,
            String nome,
            String email,
            Papel papel,
            StatusConta status,
            Instant criadoEm,
            boolean telegramVinculado,
            long propriedades,
            Instant senhaEsquecidaEm,
            GrupoResumo grupo,
            List<GrupoResumo> grupos) {}

    /** Grupo de uma conta: produtor e Admin tem um (grupo); o agronomo pode ter varios (grupos). */
    public record GrupoResumo(UUID id, String nome) {}

    public record EventoHistorico(AcaoAuditoria acao, String atorNome, String detalhe, Instant criadoEm) {}

    /** Detalhe de uma conta: dados da conta (sem hash) e o historico de acoes de gestao, do mais recente ao mais antigo. */
    public record DetalheUsuario(
            UUID id,
            String nome,
            String email,
            Papel papel,
            StatusConta status,
            boolean precisaTrocarSenha,
            String motivoSuspensao,
            Instant senhaEsquecidaEm,
            Instant criadoEm,
            List<EventoHistorico> historico) {}

    /** Resposta das acoes que geram senha temporaria: o texto claro aparece so aqui, uma unica vez. */
    public record SenhaTemporariaGerada(
            UUID id, String nome, String email, String senhaTemporaria, Instant senhaTemporariaExpiraEm) {}

    public record Resumo(
            long total,
            Map<Papel, Long> porPapel,
            long cadastrosSemana,
            long cadastrosMes,
            Instant ultimoCadastroEm,
            long pendentes,
            long senhasEsquecidas) {}

    @Transactional(readOnly = true)
    public Page<ItemUsuario> listar(
            UsuarioAutenticado solicitante,
            Papel papel,
            StatusConta status,
            boolean esqueceuSenha,
            UUID grupoId,
            String busca,
            int page,
            int pageSize) {
        autorizacaoService.exigirGestor(solicitante);

        int tamanho = Math.min(Math.max(pageSize, 1), TAMANHO_PAGINA_MAXIMO);
        Pageable pageable = PageRequest.of(
                Math.max(page - 1, 0), tamanho, Sort.by(Sort.Direction.DESC, "criadoEm").and(Sort.by("id")));
        Page<Usuario> pagina = usuarioRepository.findAll(
                escopo(solicitante).and(filtroDeGrupo(grupoId)).and(filtro(papel, status, esqueceuSenha, busca)), pageable);

        Map<UUID, Long> propriedadesPorUsuario = contarPropriedades(pagina.getContent());
        Map<UUID, GrupoResumo> gruposPorId = nomesDosGrupos(pagina.getContent());
        Map<UUID, List<GrupoResumo>> gruposDosAgronomos = gruposDosAgronomos(pagina.getContent(), gruposPorId);
        // O Admin nao ve o nome dos outros grupos de um agronomo: so o dele (isolamento entre clientes).
        if (!autorizacaoService.ehMaster(solicitante)) {
            UUID proprio = autorizacaoService.grupoDe(solicitante).orElse(null);
            gruposDosAgronomos.replaceAll((id, lista) -> lista.stream().filter(g -> g.id().equals(proprio)).toList());
        }
        List<ItemUsuario> itens = pagina.getContent().stream()
                .map(u -> new ItemUsuario(
                        u.getId(),
                        u.getNome(),
                        u.getEmail(),
                        u.getPapel(),
                        u.getStatus(),
                        u.getCriadoEm(),
                        u.getTelegramChatId() != null && !u.getTelegramChatId().isBlank(),
                        propriedadesPorUsuario.getOrDefault(u.getId(), 0L),
                        u.getSenhaEsquecidaEm(),
                        u.getGrupoId() == null ? null : gruposPorId.get(u.getGrupoId()),
                        gruposDosAgronomos.getOrDefault(u.getId(), List.of())))
                .toList();
        return new PageImpl<>(itens, pageable, pagina.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Resumo resumir(UsuarioAutenticado solicitante) {
        autorizacaoService.exigirGestor(solicitante);

        // Todas as contagens partem do escopo do solicitante: o Admin conta so o grupo dele.
        Specification<Usuario> base = escopo(solicitante);
        Instant agora = Instant.now();
        Map<Papel, Long> porPapel = new EnumMap<>(Papel.class);
        for (Papel papel : Papel.values()) {
            porPapel.put(papel, usuarioRepository.count(base.and((r, q, cb) -> cb.equal(r.get("papel"), papel))));
        }
        Instant semana = agora.minus(Duration.ofDays(7));
        Instant mes = agora.minus(Duration.ofDays(30));
        Instant ultimo = usuarioRepository
                .findAll(base, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "criadoEm")))
                .stream()
                .findFirst()
                .map(Usuario::getCriadoEm)
                .orElse(null);
        return new Resumo(
                usuarioRepository.count(base),
                porPapel,
                usuarioRepository.count(base.and((r, q, cb) -> cb.greaterThan(r.get("criadoEm"), semana))),
                usuarioRepository.count(base.and((r, q, cb) -> cb.greaterThan(r.get("criadoEm"), mes))),
                ultimo,
                usuarioRepository.count(base.and((r, q, cb) -> cb.equal(r.get("status"), StatusConta.PENDENTE))),
                usuarioRepository.count(base.and((r, q, cb) -> cb.isNotNull(r.get("senhaEsquecidaEm")))));
    }

    /** PENDENTE -> ATIVA: gera a senha temporaria, mostrada uma unica vez, e marca a troca obrigatoria. */
    @Transactional
    public SenhaTemporariaGerada confirmar(UsuarioAutenticado solicitante, UUID id, UUID grupoId) {
        autorizacaoService.exigirGestor(solicitante);
        Usuario usuario = carregarParaAtualizar(solicitante, id);
        if (usuario.getStatus() != StatusConta.PENDENTE) {
            // Duplo clique ou conta ja tratada: nao gera outra senha nem mexe na conta.
            throw new AppException(409, "A conta não está pendente de confirmação.");
        }
        String senha = geradorSenha.gerar();
        Instant agora = clock.instant();
        Instant expiraEm = agora.plus(VALIDADE_SENHA_TEMPORARIA);
        // Cadastro que chegou sem grupo: o Master escolhe (ou fica o grupo dele); o Admin so enxerga os do grupo dele.
        UUID grupo = usuario.getGrupoId() != null ? usuario.getGrupoId() : grupoDaNovaConta(solicitante, grupoId);
        usuario.confirmar(passwordEncoder.encode(senha), expiraEm);
        colocarNoGrupo(usuario, grupo, agora);
        usuarioRepository.save(usuario);
        registrar(solicitante, AcaoAuditoria.CONFIRMAR, usuario, null, agora);
        estadoContaCache.invalidar(usuario.getId());
        return new SenhaTemporariaGerada(usuario.getId(), usuario.getNome(), usuario.getEmail(), senha, expiraEm);
    }

    @Transactional(readOnly = true)
    public DetalheUsuario detalhar(UsuarioAutenticado solicitante, UUID id) {
        autorizacaoService.exigirGestor(solicitante);
        Usuario usuario = usuarioRepository.findById(id).orElseThrow(() -> new AppException(404, "Usuário não encontrado."));
        exigirVisivel(solicitante, usuario);
        List<AuditoriaConta> trilha = auditoriaRepository.findByAlvoIdOrderByCriadoEmDesc(id);
        Map<UUID, String> nomes = usuarioRepository
                .findAllById(trilha.stream().map(AuditoriaConta::getAtorId).distinct().toList()).stream()
                .collect(Collectors.toMap(Usuario::getId, u -> u.getNome() != null ? u.getNome() : u.getEmail()));
        List<EventoHistorico> historico = trilha.stream()
                .map(a -> new EventoHistorico(
                        a.getAcao(), nomes.getOrDefault(a.getAtorId(), "Conta removida"), a.getDetalhe(), a.getCriadoEm()))
                .toList();
        return new DetalheUsuario(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPapel(),
                usuario.getStatus(),
                usuario.isPrecisaTrocarSenha(),
                usuario.getMotivoSuspensao(),
                usuario.getSenhaEsquecidaEm(),
                usuario.getCriadoEm(),
                historico);
    }

    /** Conta criada direto pelo gestor: nasce ATIVA, com senha temporaria e troca obrigatoria. So produtor e agronomo. */
    @Transactional
    public SenhaTemporariaGerada criar(
            UsuarioAutenticado solicitante, String nome, String email, Papel papel, UUID grupoId) {
        autorizacaoService.exigirGestor(solicitante);
        if (papel.ehGestor()) {
            throw new AppException(400, "Gestor só entra por convite.");
        }
        UUID grupo = grupoDaNovaConta(solicitante, grupoId);
        String emailLimpo = email.trim();
        if (usuarioRepository.existsByEmailIgnoreCase(emailLimpo)) {
            throw new AppException(409, "Email já cadastrado.");
        }
        String senha = geradorSenha.gerar();
        Instant agora = clock.instant();
        Instant expiraEm = agora.plus(VALIDADE_SENHA_TEMPORARIA);
        Usuario nova = Usuario.criadaPeloGestor(
                nome.trim(), emailLimpo, papel, passwordEncoder.encode(senha), agora, expiraEm);
        Usuario usuario = usuarioRepository.save(nova);
        colocarNoGrupo(usuario, grupo, agora);
        registrar(solicitante, AcaoAuditoria.CRIAR, usuario, null, agora);
        return new SenhaTemporariaGerada(usuario.getId(), usuario.getNome(), usuario.getEmail(), senha, expiraEm);
    }

    /** Nova senha temporaria: a anterior e as sessoes abertas caem, e a pessoa troca a senha no proximo acesso. */
    @Transactional
    public SenhaTemporariaGerada redefinirSenha(UsuarioAutenticado solicitante, UUID id) {
        autorizacaoService.exigirGestor(solicitante);
        Usuario usuario = carregarParaAtualizar(solicitante, id);
        exigirPoderSobreAConta(solicitante, usuario);
        if (usuario.getStatus() == StatusConta.PENDENTE) {
            throw new AppException(409, "A conta ainda está pendente: confirme o cadastro para gerar a senha.");
        }
        String senha = geradorSenha.gerar();
        Instant agora = clock.instant();
        Instant expiraEm = agora.plus(VALIDADE_SENHA_TEMPORARIA);
        usuario.definirSenhaTemporaria(passwordEncoder.encode(senha), expiraEm);
        usuarioRepository.save(usuario);
        registrar(solicitante, AcaoAuditoria.REDEFINIR_SENHA, usuario, null, agora);
        estadoContaCache.invalidar(usuario.getId());
        return new SenhaTemporariaGerada(usuario.getId(), usuario.getNome(), usuario.getEmail(), senha, expiraEm);
    }

    /** Corrige nome e/ou e-mail. Trocar o e-mail derruba as sessoes e apaga os pedidos de recuperacao em aberto. */
    @Transactional
    public Usuario editar(UsuarioAutenticado solicitante, UUID id, String nome, String email) {
        autorizacaoService.exigirGestor(solicitante);
        if ((nome == null || nome.isBlank()) && (email == null || email.isBlank())) {
            throw new AppException(400, "Informe o nome ou o e-mail a alterar.");
        }
        Usuario usuario = carregarParaAtualizar(solicitante, id);
        exigirPoderSobreAConta(solicitante, usuario);
        String novoNome = nome == null || nome.isBlank() ? usuario.getNome() : nome.trim();
        String novoEmail = email == null || email.isBlank() ? usuario.getEmail() : email.trim();
        boolean mudouNome = !java.util.Objects.equals(novoNome, usuario.getNome());
        boolean mudouEmail = !novoEmail.equals(usuario.getEmail());
        if (mudouEmail && usuarioRepository.existsByEmailIgnoreCaseAndIdNot(novoEmail, id)) {
            throw new AppException(409, "Email já cadastrado em outra conta.");
        }
        usuario.atualizarNomeEmail(novoNome, novoEmail);
        usuarioRepository.save(usuario);
        if (mudouEmail) {
            tokenRecuperacaoRepository.apagarDoUsuario(id);
            estadoContaCache.invalidar(id);
        }
        // Registra so quais campos mudaram, nunca valores sensiveis.
        String alterados = Stream.of(mudouNome ? "nome" : null, mudouEmail ? "e-mail" : null)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining(", "));
        registrar(solicitante, AcaoAuditoria.EDITAR, usuario, alterados.isEmpty() ? "sem alteração" : "alterou: " + alterados,
                clock.instant());
        return usuario;
    }

    /** ATIVA -> SUSPENSA (mensalidade em atraso): bloqueia o login e derruba as sessoes na hora; dados preservados. */
    @Transactional
    public void suspender(UsuarioAutenticado solicitante, UUID id, String motivo) {
        autorizacaoService.exigirGestor(solicitante);
        Usuario usuario = carregarParaAtualizar(solicitante, id);
        exigirPoderSobreAConta(solicitante, usuario);
        if (usuario.getId().equals(solicitante.id())) {
            throw new AppException(409, "Você não pode suspender a própria conta.");
        }
        if (usuario.getStatus() != StatusConta.ATIVA) {
            throw new AppException(409, "Só é possível suspender uma conta ativa.");
        }
        if (usuario.getPapel() == Papel.ADMIN_MASTER
                && usuarioRepository.countByPapelAndStatus(Papel.ADMIN_MASTER, StatusConta.ATIVA) <= 1) {
            throw new AppException(409, "Não é possível suspender o último administrador da plataforma ativo.");
        }
        String motivoLimpo = motivo == null || motivo.isBlank() ? null : motivo.trim();
        Instant agora = clock.instant();
        usuario.suspender(motivoLimpo, agora);
        usuarioRepository.save(usuario);
        registrar(solicitante, AcaoAuditoria.SUSPENDER, usuario, motivoLimpo, agora);
        estadoContaCache.invalidar(usuario.getId());
    }

    /** SUSPENSA -> ATIVA: volta como estava, sem trocar a senha. */
    @Transactional
    public void reativar(UsuarioAutenticado solicitante, UUID id) {
        autorizacaoService.exigirGestor(solicitante);
        Usuario usuario = carregarParaAtualizar(solicitante, id);
        exigirPoderSobreAConta(solicitante, usuario);
        if (usuario.getStatus() != StatusConta.SUSPENSA) {
            throw new AppException(409, "A conta não está suspensa.");
        }
        usuario.reativar();
        usuarioRepository.save(usuario);
        registrar(solicitante, AcaoAuditoria.REATIVAR, usuario, null, clock.instant());
        estadoContaCache.invalidar(usuario.getId());
    }

    /** Recusar apaga o cadastro pendente; a auditoria guarda o e-mail recusado (a conta deixa de existir). */
    @Transactional
    public void recusar(UsuarioAutenticado solicitante, UUID id) {
        autorizacaoService.exigirGestor(solicitante);
        Usuario usuario = carregarParaAtualizar(solicitante, id);
        if (usuario.getStatus() != StatusConta.PENDENTE) {
            throw new AppException(409, "Só é possível recusar um cadastro pendente.");
        }
        auditoriaRepository.save(new AuditoriaConta(
                solicitante.id(), AcaoAuditoria.RECUSAR, null, usuario.getEmail(), null, clock.instant()));
        usuarioRepository.delete(usuario);
        estadoContaCache.invalidar(id);
    }

    /** Carrega com bloqueio de linha e so devolve contas do escopo do solicitante: fora dele, 404 (spec 024). */
    private Usuario carregarParaAtualizar(UsuarioAutenticado solicitante, UUID id) {
        Usuario usuario = usuarioRepository.buscarParaAtualizar(id)
                .orElseThrow(() -> new AppException(404, "Usuário não encontrado."));
        exigirVisivel(solicitante, usuario);
        return usuario;
    }

    /** O Master ve tudo; o Admin ve so produtores e Admins do grupo dele e agronomos que participam dele, nunca o Master. */
    private void exigirVisivel(UsuarioAutenticado solicitante, Usuario alvo) {
        if (autorizacaoService.ehMaster(solicitante)) {
            return;
        }
        UUID grupo = autorizacaoService.grupoDe(solicitante).orElse(null);
        boolean visivel = grupo != null
                && alvo.getPapel() != Papel.ADMIN_MASTER
                && (grupo.equals(alvo.getGrupoId())
                        || (alvo.getPapel() == Papel.AGRONOMO
                                && participacaoRepository.findByIdAgronomoId(alvo.getId()).stream()
                                        .anyMatch(p -> p.getGrupoId().equals(grupo))));
        if (!visivel) {
            throw new AppException(404, "Usuário não encontrado.");
        }
    }

    /** Agronomo de mais de um grupo: o Admin nao suspende, reativa, redefine a senha nem edita (FR-016); e do Master. */
    private void exigirPoderSobreAConta(UsuarioAutenticado solicitante, Usuario alvo) {
        if (autorizacaoService.ehMaster(solicitante) || alvo.getPapel() != Papel.AGRONOMO) {
            return;
        }
        if (participacaoRepository.countByIdAgronomoId(alvo.getId()) > 1) {
            throw new AppException(403,
                    "Esta conta pertence a mais de um grupo: peça ao administrador da plataforma.");
        }
    }

    /** Grupo de uma conta nova ou confirmada: o Master escolhe (ou fica o grupo dele); o Admin so no grupo dele. */
    private UUID grupoDaNovaConta(UsuarioAutenticado solicitante, UUID grupoPedido) {
        UUID proprio = autorizacaoService.grupoDe(solicitante).orElse(null);
        UUID grupo;
        if (autorizacaoService.ehMaster(solicitante)) {
            grupo = grupoPedido != null ? grupoPedido : proprio;
        } else {
            if (grupoPedido != null && !grupoPedido.equals(proprio)) {
                throw new AppException(404, "Grupo não encontrado.");
            }
            grupo = proprio;
        }
        if (grupo == null) {
            throw new AppException(422, "Informe o grupo da conta.");
        }
        if (!grupoRepository.existsById(grupo)) {
            throw new AppException(404, "Grupo não encontrado.");
        }
        return grupo;
    }

    /** Produtor guarda o grupo na conta; agronomo ganha uma participacao (pode ter varias). */
    private void colocarNoGrupo(Usuario usuario, UUID grupo, Instant agora) {
        if (usuario.getPapel() == Papel.AGRONOMO) {
            if (participacaoRepository.countByIdAgronomoId(usuario.getId()) == 0) {
                participacaoRepository.save(new ParticipacaoAgronomo(usuario.getId(), grupo, agora));
            }
        } else {
            usuario.definirGrupo(grupo);
        }
    }

    private void registrar(UsuarioAutenticado ator, AcaoAuditoria acao, Usuario alvo, String detalhe, Instant agora) {
        auditoriaRepository.save(new AuditoriaConta(ator.id(), acao, alvo.getId(), alvo.getEmail(), detalhe, agora));
    }

    /**
     * Quais contas o solicitante enxerga: o Master, todas; o Admin, os produtores e Admins do grupo dele e os
     * agronomos que participam dele, nunca o Master; sem grupo, nenhuma. Pendentes sem grupo so o Master ve.
     */
    private Specification<Usuario> escopo(UsuarioAutenticado solicitante) {
        if (autorizacaoService.ehMaster(solicitante)) {
            return (root, query, cb) -> cb.conjunction();
        }
        UUID grupo = autorizacaoService.grupoDe(solicitante).orElse(null);
        if (grupo == null) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> {
            Subquery<UUID> doGrupo = query.subquery(UUID.class);
            Root<ParticipacaoAgronomo> participacao = doGrupo.from(ParticipacaoAgronomo.class);
            doGrupo.select(participacao.get("id").get("agronomoId"))
                    .where(cb.equal(participacao.get("id").get("grupoId"), grupo));
            return cb.and(
                    cb.notEqual(root.get("papel"), Papel.ADMIN_MASTER),
                    cb.or(cb.equal(root.get("grupoId"), grupo), root.get("id").in(doGrupo)));
        };
    }

    /** Filtro opcional por grupo (o Master escolhe um): contas do grupo ou agronomos que participam dele. */
    private Specification<Usuario> filtroDeGrupo(UUID grupoId) {
        if (grupoId == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            Subquery<UUID> doGrupo = query.subquery(UUID.class);
            Root<ParticipacaoAgronomo> participacao = doGrupo.from(ParticipacaoAgronomo.class);
            doGrupo.select(participacao.get("id").get("agronomoId"))
                    .where(cb.equal(participacao.get("id").get("grupoId"), grupoId));
            return cb.or(cb.equal(root.get("grupoId"), grupoId), root.get("id").in(doGrupo));
        };
    }

    private Map<UUID, GrupoResumo> nomesDosGrupos(List<Usuario> usuarios) {
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        usuarios.stream().map(Usuario::getGrupoId).filter(java.util.Objects::nonNull).forEach(ids::add);
        List<UUID> agronomos = idsDoPapel(usuarios, Papel.AGRONOMO);
        if (!agronomos.isEmpty()) {
            participacaoRepository.findByIdAgronomoIdIn(agronomos).forEach(p -> ids.add(p.getGrupoId()));
        }
        Map<UUID, GrupoResumo> nomes = new java.util.HashMap<>();
        for (Grupo grupo : grupoRepository.findAllById(ids)) {
            nomes.put(grupo.getId(), new GrupoResumo(grupo.getId(), grupo.getNome()));
        }
        return nomes;
    }

    private Map<UUID, List<GrupoResumo>> gruposDosAgronomos(List<Usuario> usuarios, Map<UUID, GrupoResumo> nomes) {
        List<UUID> agronomos = idsDoPapel(usuarios, Papel.AGRONOMO);
        if (agronomos.isEmpty()) {
            return new java.util.HashMap<>();
        }
        Map<UUID, List<GrupoResumo>> resultado = new java.util.HashMap<>();
        for (ParticipacaoAgronomo p : participacaoRepository.findByIdAgronomoIdIn(agronomos)) {
            GrupoResumo grupo = nomes.get(p.getGrupoId());
            if (grupo != null) {
                resultado.computeIfAbsent(p.getAgronomoId(), k -> new ArrayList<>()).add(grupo);
            }
        }
        return resultado;
    }

    private Specification<Usuario> filtro(Papel papel, StatusConta status, boolean esqueceuSenha, String busca) {
        return (root, query, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();
            if (papel != null) {
                condicoes.add(cb.equal(root.get("papel"), papel));
            }
            if (esqueceuSenha) {
                condicoes.add(cb.isNotNull(root.get("senhaEsquecidaEm")));
            }
            if (status != null) {
                condicoes.add(cb.equal(root.get("status"), status));
            }
            if (busca != null && !busca.isBlank()) {
                String padrao = "%" + escaparCuringas(busca.trim().toLowerCase(Locale.ROOT)) + "%";
                condicoes.add(cb.or(
                        cb.like(cb.lower(root.get("nome")), padrao, '\\'),
                        cb.like(cb.lower(root.get("email")), padrao, '\\')));
            }
            return cb.and(condicoes.toArray(new Predicate[0]));
        };
    }

    /** O termo digitado e literal: %, _ e \ nao viram curinga do LIKE. */
    private static String escaparCuringas(String termo) {
        return termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Map<UUID, Long> contarPropriedades(List<Usuario> usuarios) {
        List<UUID> produtores = idsDoPapel(usuarios, Papel.PRODUTOR_RURAL);
        List<UUID> agronomos = idsDoPapel(usuarios, Papel.AGRONOMO);
        Map<UUID, Long> contagem = new java.util.HashMap<>();
        if (!produtores.isEmpty()) {
            contagem.putAll(paraMapa(propriedadeRepository.contarPorProprietario(produtores)));
        }
        if (!agronomos.isEmpty()) {
            contagem.putAll(paraMapa(vinculoRepository.contarPorAgronomo(agronomos, EstadoVinculo.ACEITO)));
        }
        return contagem;
    }

    private static List<UUID> idsDoPapel(List<Usuario> usuarios, Papel papel) {
        return usuarios.stream().filter(u -> u.getPapel() == papel).map(Usuario::getId).toList();
    }

    private static Map<UUID, Long> paraMapa(List<ContagemPorUsuario> contagens) {
        return contagens.stream().collect(Collectors.toMap(ContagemPorUsuario::getUsuarioId, ContagemPorUsuario::getTotal));
    }
}
