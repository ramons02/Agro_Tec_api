package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.GeradorSenhaTemporaria;
import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.StatusConta;
import com.agroclima.api.business.auth.TokenRecuperacaoSenhaRepository;
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
            Clock clock) {
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
            Instant senhaEsquecidaEm) {}

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
            String busca,
            int page,
            int pageSize) {
        autorizacaoService.exigirGestor(solicitante);

        int tamanho = Math.min(Math.max(pageSize, 1), TAMANHO_PAGINA_MAXIMO);
        Pageable pageable = PageRequest.of(
                Math.max(page - 1, 0), tamanho, Sort.by(Sort.Direction.DESC, "criadoEm").and(Sort.by("id")));
        Page<Usuario> pagina = usuarioRepository.findAll(filtro(papel, status, esqueceuSenha, busca), pageable);

        Map<UUID, Long> propriedadesPorUsuario = contarPropriedades(pagina.getContent());
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
                        u.getSenhaEsquecidaEm()))
                .toList();
        return new PageImpl<>(itens, pageable, pagina.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Resumo resumir(UsuarioAutenticado solicitante) {
        autorizacaoService.exigirGestor(solicitante);

        Instant agora = Instant.now();
        Map<Papel, Long> porPapel = new EnumMap<>(Papel.class);
        for (Papel papel : Papel.values()) {
            porPapel.put(papel, usuarioRepository.countByPapel(papel));
        }
        return new Resumo(
                usuarioRepository.count(),
                porPapel,
                usuarioRepository.countByCriadoEmAfter(agora.minus(Duration.ofDays(7))),
                usuarioRepository.countByCriadoEmAfter(agora.minus(Duration.ofDays(30))),
                usuarioRepository.findFirstByOrderByCriadoEmDesc().map(Usuario::getCriadoEm).orElse(null),
                usuarioRepository.countByStatus(StatusConta.PENDENTE),
                usuarioRepository.countBySenhaEsquecidaEmIsNotNull());
    }

    /** PENDENTE -> ATIVA: gera a senha temporaria, mostrada uma unica vez, e marca a troca obrigatoria. */
    @Transactional
    public SenhaTemporariaGerada confirmar(UsuarioAutenticado solicitante, UUID id) {
        autorizacaoService.exigirGestor(solicitante);
        Usuario usuario = carregarParaAtualizar(id);
        if (usuario.getStatus() != StatusConta.PENDENTE) {
            // Duplo clique ou conta ja tratada: nao gera outra senha nem mexe na conta.
            throw new AppException(409, "A conta não está pendente de confirmação.");
        }
        String senha = geradorSenha.gerar();
        Instant agora = clock.instant();
        Instant expiraEm = agora.plus(VALIDADE_SENHA_TEMPORARIA);
        usuario.confirmar(passwordEncoder.encode(senha), expiraEm);
        usuarioRepository.save(usuario);
        registrar(solicitante, AcaoAuditoria.CONFIRMAR, usuario, null, agora);
        estadoContaCache.invalidar(usuario.getId());
        return new SenhaTemporariaGerada(usuario.getId(), usuario.getNome(), usuario.getEmail(), senha, expiraEm);
    }

    @Transactional(readOnly = true)
    public DetalheUsuario detalhar(UsuarioAutenticado solicitante, UUID id) {
        autorizacaoService.exigirGestor(solicitante);
        Usuario usuario = usuarioRepository.findById(id).orElseThrow(() -> new AppException(404, "Usuário não encontrado."));
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
    public SenhaTemporariaGerada criar(UsuarioAutenticado solicitante, String nome, String email, Papel papel) {
        autorizacaoService.exigirGestor(solicitante);
        if (papel == Papel.GESTOR_TECNOLOGIA) {
            throw new AppException(400, "Gestor só entra por convite.");
        }
        String emailLimpo = email.trim();
        if (usuarioRepository.existsByEmailIgnoreCase(emailLimpo)) {
            throw new AppException(409, "Email já cadastrado.");
        }
        String senha = geradorSenha.gerar();
        Instant agora = clock.instant();
        Instant expiraEm = agora.plus(VALIDADE_SENHA_TEMPORARIA);
        Usuario usuario = usuarioRepository.save(Usuario.criadaPeloGestor(
                nome.trim(), emailLimpo, papel, passwordEncoder.encode(senha), agora, expiraEm));
        registrar(solicitante, AcaoAuditoria.CRIAR, usuario, null, agora);
        return new SenhaTemporariaGerada(usuario.getId(), usuario.getNome(), usuario.getEmail(), senha, expiraEm);
    }

    /** Nova senha temporaria: a anterior e as sessoes abertas caem, e a pessoa troca a senha no proximo acesso. */
    @Transactional
    public SenhaTemporariaGerada redefinirSenha(UsuarioAutenticado solicitante, UUID id) {
        autorizacaoService.exigirGestor(solicitante);
        Usuario usuario = carregarParaAtualizar(id);
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
        Usuario usuario = carregarParaAtualizar(id);
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
        Usuario usuario = carregarParaAtualizar(id);
        if (usuario.getId().equals(solicitante.id())) {
            throw new AppException(409, "Você não pode suspender a própria conta.");
        }
        if (usuario.getStatus() != StatusConta.ATIVA) {
            throw new AppException(409, "Só é possível suspender uma conta ativa.");
        }
        if (usuario.getPapel() == Papel.GESTOR_TECNOLOGIA
                && usuarioRepository.countByPapelAndStatus(Papel.GESTOR_TECNOLOGIA, StatusConta.ATIVA) <= 1) {
            throw new AppException(409, "Não é possível suspender o último gestor ativo.");
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
        Usuario usuario = carregarParaAtualizar(id);
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
        Usuario usuario = carregarParaAtualizar(id);
        if (usuario.getStatus() != StatusConta.PENDENTE) {
            throw new AppException(409, "Só é possível recusar um cadastro pendente.");
        }
        auditoriaRepository.save(new AuditoriaConta(
                solicitante.id(), AcaoAuditoria.RECUSAR, null, usuario.getEmail(), null, clock.instant()));
        usuarioRepository.delete(usuario);
        estadoContaCache.invalidar(id);
    }

    private Usuario carregarParaAtualizar(UUID id) {
        return usuarioRepository.buscarParaAtualizar(id)
                .orElseThrow(() -> new AppException(404, "Usuário não encontrado."));
    }

    private void registrar(UsuarioAutenticado ator, AcaoAuditoria acao, Usuario alvo, String detalhe, Instant agora) {
        auditoriaRepository.save(new AuditoriaConta(ator.id(), acao, alvo.getId(), alvo.getEmail(), detalhe, agora));
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
