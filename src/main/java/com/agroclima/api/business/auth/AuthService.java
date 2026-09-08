package com.agroclima.api.business.auth;

import com.agroclima.api.core.config.AppProperties;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/** Espelha os 4 endpoints de auth.py: login, registro, recuperar-senha, redefinir-senha. */
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final TokenRecuperacaoSenhaRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UsuarioRepository usuarioRepository,
            TokenRecuperacaoSenhaRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailService emailService,
            AppProperties appProperties) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.appProperties = appProperties;
    }

    public record LoginResultado(String token, Instant expiraEm, Papel papel) {}

    @Transactional(readOnly = true)
    public LoginResultado login(String email, String senha) {
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        // Mesma mensagem genérica pra usuário inexistente e senha errada -- nunca revela qual das duas.
        if (usuario == null || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new AppException(401, "Usuário ou senha inválidos.");
        }
        JwtService.TokenGerado tokenGerado = jwtService.criarToken(usuario.getId(), usuario.getPapel());
        return new LoginResultado(tokenGerado.token(), tokenGerado.expiraEm(), usuario.getPapel());
    }

    @Transactional
    public Usuario registrar(String nome, String email, String senha, Papel papel) {
        if (usuarioRepository.existsByEmail(email)) {
            throw new AppException(409, "Email já cadastrado.");
        }
        Usuario usuario = new Usuario(nome, email, passwordEncoder.encode(senha), papel);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void solicitarRecuperacaoSenha(String email) {
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
