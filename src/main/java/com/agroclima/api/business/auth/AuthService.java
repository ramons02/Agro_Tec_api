package com.agroclima.api.business.auth;

import com.agroclima.api.business.usuario.AcaoAuditoria;
import com.agroclima.api.business.usuario.AuditoriaConta;
import com.agroclima.api.business.usuario.AuditoriaContaRepository;
import com.agroclima.api.business.grupo.ParticipacaoAgronomo;
import com.agroclima.api.business.grupo.ParticipacaoAgronomoRepository;
import com.agroclima.api.core.config.AppProperties;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.security.EstadoContaCache;
import com.agroclima.api.core.security.JwtService;
import com.agroclima.api.core.security.LimitadorTentativas;
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
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Espelha os 4 endpoints de auth.py: login, registro, recuperar-senha, redefinir-senha. */
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final TokenRecuperacaoSenhaRepository tokenRepository;
    private final ConviteGestorRepository conviteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final EstadoContaCache estadoContaCache;
    private final Clock clock;
    private final AuditoriaContaRepository auditoriaRepository;
    private final ParticipacaoAgronomoRepository participacaoRepository;
    static final int SENHA_MINIMA = 8;
    private final SecureRandom secureRandom = new SecureRandom();
    // 5 logins errados por e-mail bloqueiam por 15 min; 3 pedidos de recuperacao por e-mail por hora.
    private final LimitadorTentativas limiteLogin = new LimitadorTentativas(5, Duration.ofMinutes(15));
    private final LimitadorTentativas limiteRecuperacao = new LimitadorTentativas(3, Duration.ofHours(1));
    // 5 senhas atuais erradas por conta em 15 min: quem roubou um token nao descobre a senha por tentativa e erro.
    private final LimitadorTentativas limiteTrocaSenha = new LimitadorTentativas(5, Duration.ofMinutes(15));

    public AuthService(
            UsuarioRepository usuarioRepository,
            TokenRecuperacaoSenhaRepository tokenRepository,
            ConviteGestorRepository conviteRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailService emailService,
            AppProperties appProperties,
            EstadoContaCache estadoContaCache,
            Clock clock,
            AuditoriaContaRepository auditoriaRepository,
            ParticipacaoAgronomoRepository participacaoRepository) {
        this.participacaoRepository = participacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.conviteRepository = conviteRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.appProperties = appProperties;
        this.estadoContaCache = estadoContaCache;
        this.clock = clock;
        this.auditoriaRepository = auditoriaRepository;
    }

    public record LoginResultado(String token, Instant expiraEm, Papel papel, boolean precisaTrocarSenha) {}

    @Transactional
    public LoginResultado login(String email, String senha) {
        // Conta por e-mail exista ou nao o usuario, entao o 429 tambem nao revela se a conta existe.
        if (limiteLogin.bloqueado(email)) {
            throw new AppException(429, "Muitas tentativas de login. Tente novamente em alguns minutos.");
        }
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        // Mesma mensagem genérica pra usuário inexistente e senha errada -- nunca revela qual das duas.
        // Conta PENDENTE nao tem senha: e indistinguivel de e-mail inexistente (nao revela que a conta existe).
        if (usuario == null
                || usuario.getStatus() == StatusConta.PENDENTE
                || usuario.getSenhaHash() == null
                || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            limiteLogin.registrarFalha(email);
            throw new AppException(401, "Usuário ou senha inválidos.");
        }
        // Estados so sao revelados depois da senha correta, para o login nao confirmar quais e-mails existem.
        if (usuario.getStatus() == StatusConta.SUSPENSA) {
            throw new AppException(403, "Conta suspensa. Procure o administrador.");
        }
        Instant expiraSenhaTemporaria = usuario.getSenhaTemporariaExpiraEm();
        if (expiraSenhaTemporaria != null && expiraSenhaTemporaria.isBefore(clock.instant())) {
            throw new AppException(403, "Senha temporária expirada. Procure o administrador para redefinir.");
        }
        limiteLogin.limpar(email);
        if (usuario.getSenhaEsquecidaEm() != null) {
            usuario.limparPedidoDeNovaSenha();
            usuarioRepository.save(usuario);
        }
        // Com troca obrigatoria o token e restrito: so serve para definir a senha nova (POST /auth/trocar-senha).
        JwtService.TokenGerado tokenGerado = usuario.isPrecisaTrocarSenha()
                ? jwtService.criarTokenRestrito(usuario.getId(), usuario.getPapel(), usuario.getVersaoSessao())
                : jwtService.criarToken(usuario.getId(), usuario.getPapel(), usuario.getVersaoSessao());
        return new LoginResultado(
                tokenGerado.token(), tokenGerado.expiraEm(), usuario.getPapel(), usuario.isPrecisaTrocarSenha());
    }

    public record SessaoRenovada(String token, Instant expiraEm, Papel papel) {}

    /**
     * Primeiro acesso (senha atual = temporaria, qualquer papel) e troca voluntaria (so gestor): mesma regra. Sucesso limpa a troca
     * obrigatoria e a expiracao, derruba as outras sessoes da conta (versao de sessao) e devolve token completo.
     */
    @Transactional
    public SessaoRenovada trocarSenha(UsuarioAutenticado solicitante, String senhaAtual, String senhaNova) {
        String chave = solicitante.id().toString();
        if (limiteTrocaSenha.bloqueado(chave)) {
            throw new AppException(429, "Muitas tentativas. Tente novamente em alguns minutos.");
        }
        Usuario usuario = usuarioRepository.buscarParaAtualizar(solicitante.id())
                .orElseThrow(() -> new AppException(401, "Token de autorização ausente ou expirado."));
        // So o gestor troca a propria senha quando quiser; produtor e agronomo so a definem no primeiro acesso
        // (senha temporaria) e, depois, quem redefine e o gestor.
        if (!usuario.isPrecisaTrocarSenha() && !usuario.getPapel().ehGestor()) {
            throw new AppException(403, "Somente o administrador pode redefinir a sua senha.");
        }
        // 400 e nao 401: o app trata 401 como sessao expirada e deslogaria quem so errou a senha atual.
        if (usuario.getSenhaHash() == null || !passwordEncoder.matches(senhaAtual, usuario.getSenhaHash())) {
            limiteTrocaSenha.registrarFalha(chave);
            throw new AppException(400, "Senha atual incorreta.");
        }
        if (senhaAtual.equals(senhaNova)) {
            throw new AppException(400, "A senha nova precisa ser diferente da atual.");
        }
        limiteTrocaSenha.limpar(chave);
        usuario.trocarSenha(passwordEncoder.encode(senhaNova));
        usuarioRepository.save(usuario);
        estadoContaCache.invalidar(usuario.getId());
        JwtService.TokenGerado token = jwtService.criarToken(usuario.getId(), usuario.getPapel(), usuario.getVersaoSessao());
        return new SessaoRenovada(token.token(), token.expiraEm(), usuario.getPapel());
    }

    @Transactional
    public Usuario registrar(String nome, String email, String senha, Papel papel, String convite) {
        return registrar(nome, email, senha, papel, convite, null);
    }

    /**
     * grupoId (opcional) vem do link de cadastro do grupo, ja validado: a conta nasce pendente nesse grupo, visivel ao
     * Admin dele e ao Master (spec 024, FR-009). Sem ele, pendente sem grupo: so o Master ve e atribui o grupo.
     */
    @Transactional
    public Usuario registrar(String nome, String email, String senha, Papel papel, String convite, UUID grupoId) {
        if (papel == Papel.GESTOR_TECNOLOGIA) {
            // Admin nasce por criacao direta do Master (spec 024); nem o cadastro nem convite criam Admin.
            throw new AppException(403, "O Admin de um grupo é criado pelo administrador da plataforma.");
        }
        // Master nasce so por convite de outro Master (uso unico, amarrado ao e-mail).
        boolean gestor = papel == Papel.ADMIN_MASTER;
        if (gestor) {
            consumirConviteGestor(convite, email);
            if (senha == null || senha.length() < SENHA_MINIMA) {
                throw new AppException(400, "Informe uma senha com no mínimo " + SENHA_MINIMA + " caracteres.");
            }
        } else if (senha != null && !senha.isBlank()) {
            throw new AppException(400,
                    "O cadastro não recebe senha: o administrador gera uma senha temporária ao confirmar a conta.");
        }
        if (usuarioRepository.existsByEmailIgnoreCase(email.trim())) {
            throw new AppException(409, "Email já cadastrado.");
        }
        // Gestor (por convite) escolhe a senha e ja entra ATIVO; os demais ficam PENDENTES, sem senha.
        Usuario usuario = gestor
                ? new Usuario(nome, email, passwordEncoder.encode(senha), papel)
                : Usuario.pendente(nome, email, papel, clock.instant());
        if (!gestor && grupoId != null && papel == Papel.PRODUTOR_RURAL) {
            usuario.definirGrupo(grupoId);
        }
        Usuario salvo = usuarioRepository.save(usuario);
        if (!gestor && grupoId != null && papel == Papel.AGRONOMO) {
            participacaoRepository.save(new ParticipacaoAgronomo(salvo.getId(), grupoId, clock.instant()));
        }
        return salvo;
    }

    public record ConviteCriado(String link, Instant expiraEm) {}

    @Transactional
    public ConviteCriado criarConviteMaster(UsuarioAutenticado criador, String email) {
        if (criador.papel() != Papel.ADMIN_MASTER) {
            // Admin nao cria Admin nem Master (spec 024, FR-004, FR-017): so o Master convida outro Master.
            throw new AppException(403, "Somente o administrador da plataforma pode convidar outro administrador.");
        }
        String token = gerarTokenOpaco();
        ConviteGestor convite = new ConviteGestor(hashDoToken(token), email.trim(), criador.id());
        conviteRepository.save(convite);
        String link = appProperties.frontendBaseUrl() + "/#/cadastro?convite=" + token;
        return new ConviteCriado(link, convite.getExpiraEm());
    }

    private void consumirConviteGestor(String token, String email) {
        Instant agora = Instant.now();
        ConviteGestor convite = (token == null || token.isBlank())
                ? null
                : conviteRepository.findByTokenHash(hashDoToken(token.trim())).orElse(null);
        if (convite == null || !convite.estaValidoPara(email, agora)
                || !Papel.ADMIN_MASTER.name().equals(convite.getPapelDestino())) {
            throw new AppException(403, "Convite inválido, expirado ou emitido para outro e-mail.");
        }
        convite.marcarUsado(agora);
        conviteRepository.save(convite);
    }

    private static String hashDoToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Transactional
    public void solicitarRecuperacaoSenha(String email) {
        if (!limiteRecuperacao.consumir(email)) {
            // Mesma resposta de sempre (anti-enumeracao), so nao envia mais e-mail.
            return;
        }
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        if (usuario == null || usuario.getStatus() == StatusConta.PENDENTE) {
            // RF032 / anti-enumeracao: resposta ao endpoint e sempre a mesma, sem efeito colateral aqui.
            // Conta PENDENTE tambem nao recebe nada: ainda nao foi aprovada pelo administrador.
            return;
        }
        if (usuario.getStatus() == StatusConta.SUSPENSA && !usuario.getPapel().ehGestor()) {
            // Conta suspensa nao entra: nao ha o que redefinir ate o gestor reativar.
            return;
        }
        if (!usuario.getPapel().ehGestor()) {
            // Produtor e agronomo nao recebem link por e-mail: o pedido vira uma pendencia na tela do
            // gestor, que gera a senha temporaria e a repassa. A resposta ao endpoint continua a mesma.
            Instant agora = clock.instant();
            boolean primeiroPedido = usuario.getSenhaEsquecidaEm() == null;
            usuario.pedirNovaSenha(agora);
            usuarioRepository.save(usuario);
            if (primeiroPedido) {
                auditoriaRepository.save(new AuditoriaConta(usuario.getId(), AcaoAuditoria.SENHA_ESQUECIDA,
                        usuario.getId(), usuario.getEmail(), "pediu nova senha na tela de login", agora));
            }
            return;
        }
        String token = gerarTokenOpaco();
        tokenRepository.save(new TokenRecuperacaoSenha(usuario.getId(), token));
        String link = appProperties.frontendBaseUrl() + "/#/redefinir-senha?token=" + token;
        emailService.enviarRecuperacaoSenha(usuario.getEmail(), link);
    }

    @Transactional
    public void redefinirSenha(String token, String novaSenha) {
        Instant agora = Instant.now();
        TokenRecuperacaoSenha tokenEntity = tokenRepository.findByToken(token).orElse(null);
        if (tokenEntity == null || !tokenEntity.estaValido(agora)) {
            throw new AppException(400, "Link de redefinição inválido ou expirado. Solicite um novo.");
        }
        Usuario usuario = usuarioRepository.findById(tokenEntity.getUsuarioId())
                .orElseThrow(() -> new AppException(400, "Link de redefinição inválido ou expirado. Solicite um novo."));
        if (usuario.getStatus() == StatusConta.PENDENTE) {
            // Mesmo um token forjado ou antigo nao pode dar senha a quem o administrador ainda nao aprovou.
            throw new AppException(400, "Link de redefinição inválido ou expirado. Solicite um novo.");
        }
        // Conta SUSPENSA pode definir a senha, mas continua suspensa (o login segue negado).
        usuario.atualizarSenha(passwordEncoder.encode(novaSenha));
        tokenEntity.marcarUsado(agora);
        usuarioRepository.save(usuario);
        tokenRepository.save(tokenEntity);
        estadoContaCache.invalidar(usuario.getId());
    }

    private String gerarTokenOpaco() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
