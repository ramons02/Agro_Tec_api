package com.agroclima.api.business.auth;

import com.agroclima.api.core.config.AppProperties;
import com.agroclima.api.core.response.AppException;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

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
    private final SecureRandom secureRandom = new SecureRandom();
    // 5 logins errados por e-mail bloqueiam por 15 min; 3 pedidos de recuperacao por e-mail por hora.
    private final LimitadorTentativas limiteLogin = new LimitadorTentativas(5, Duration.ofMinutes(15));
    private final LimitadorTentativas limiteRecuperacao = new LimitadorTentativas(3, Duration.ofHours(1));

    public AuthService(
            UsuarioRepository usuarioRepository,
            TokenRecuperacaoSenhaRepository tokenRepository,
            ConviteGestorRepository conviteRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailService emailService,
            AppProperties appProperties) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.conviteRepository = conviteRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.appProperties = appProperties;
    }

    public record LoginResultado(String token, Instant expiraEm, Papel papel) {}

    @Transactional(readOnly = true)
    public LoginResultado login(String email, String senha) {
        // Conta por e-mail exista ou nao o usuario, entao o 429 tambem nao revela se a conta existe.
        if (limiteLogin.bloqueado(email)) {
            throw new AppException(429, "Muitas tentativas de login. Tente novamente em alguns minutos.");
        }
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        // Mesma mensagem genérica pra usuário inexistente e senha errada -- nunca revela qual das duas.
        if (usuario == null || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            limiteLogin.registrarFalha(email);
            throw new AppException(401, "Usuário ou senha inválidos.");
        }
        limiteLogin.limpar(email);
        JwtService.TokenGerado tokenGerado = jwtService.criarToken(usuario.getId(), usuario.getPapel());
        return new LoginResultado(tokenGerado.token(), tokenGerado.expiraEm(), usuario.getPapel());
    }

    @Transactional
    public Usuario registrar(String nome, String email, String senha, Papel papel, String convite) {
        if (papel == Papel.GESTOR_TECNOLOGIA) {
            // Registro publico nunca cria gestor sem convite valido (uso unico, amarrado ao e-mail).
            consumirConviteGestor(convite, email);
        }
        if (usuarioRepository.existsByEmail(email)) {
            throw new AppException(409, "Email já cadastrado.");
        }
        Usuario usuario = new Usuario(nome, email, passwordEncoder.encode(senha), papel);
        return usuarioRepository.save(usuario);
    }

    public record ConviteCriado(String link, Instant expiraEm) {}

    @Transactional
    public ConviteCriado criarConviteGestor(UsuarioAutenticado criador, String email) {
        if (criador.papel() != Papel.GESTOR_TECNOLOGIA) {
            throw new AppException(403, "Somente gestor de tecnologia pode convidar outro gestor.");
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
        if (convite == null || !convite.estaValidoPara(email, agora)) {
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
        if (usuario == null) {
            // RF032 / anti-enumeracao: resposta ao endpoint e sempre a mesma, sem efeito colateral aqui.
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
        usuario.atualizarSenha(passwordEncoder.encode(novaSenha));
        tokenEntity.marcarUsado(agora);
        usuarioRepository.save(usuario);
        tokenRepository.save(tokenEntity);
    }

    private String gerarTokenOpaco() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
