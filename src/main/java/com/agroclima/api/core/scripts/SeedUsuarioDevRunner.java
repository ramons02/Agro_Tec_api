package com.agroclima.api.core.scripts;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Espelha app/scripts/seed_usuario_dev.py -- java -jar app.jar --spring.profiles.active=dev
 * seed-usuario-dev [email] [senha] [papel]. So roda quando chamado explicitamente.
 * Diferenca do Python: se o usuario ja existe, so a senha e atualizada (papel fica como
 * estava) -- Usuario nao expoe um setter de papel, e este e um script de conveniencia de
 * dev, nao vale criar mutabilidade de dominio so por causa dele.
 */
@Component
@Profile("dev")
public class SeedUsuarioDevRunner implements CommandLineRunner {

    private static final String COMANDO = "seed-usuario-dev";
    private static final Logger log = LoggerFactory.getLogger(SeedUsuarioDevRunner.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedUsuarioDevRunner(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (args.length == 0 || !COMANDO.equals(args[0])) {
            return;
        }
        String email = args.length > 1 ? args[1] : "produtor@agroclima.dev";
        String senha = args.length > 2 ? args[2] : "agroclima123";
        Papel papel = args.length > 3 ? Papel.valueOf(args[3]) : Papel.PRODUTOR_RURAL;
        String senhaHash = passwordEncoder.encode(senha);

        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        if (usuario == null) {
            usuario = usuarioRepository.save(new Usuario(null, email, senhaHash, papel));
        } else {
            usuario.atualizarSenha(senhaHash);
            usuario = usuarioRepository.save(usuario);
        }
        log.info("Usuário dev pronto: id={} email={} papel={}", usuario.getId(), usuario.getEmail(), usuario.getPapel());
    }
}
