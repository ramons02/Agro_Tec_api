package com.agroclima.api.business.auth;

import com.agroclima.api.business.grupo.GrupoService;
import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.security.LimitadorCadastro;
import com.agroclima.api.core.security.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Espelha app/api/v1/endpoints/auth.py. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final LimitadorCadastro limitadorCadastro;
    private final GrupoService grupoService;

    public AuthController(AuthService authService, LimitadorCadastro limitadorCadastro, GrupoService grupoService) {
        this.authService = authService;
        this.limitadorCadastro = limitadorCadastro;
        this.grupoService = grupoService;
    }

    public record LoginRequest(@Email @NotBlank @Size(max = 255) String email, @NotBlank @Size(max = 128) String senha) {}

    public record RegistroRequest(
            @NotBlank @Size(max = 255) String nome,
            @Email @NotBlank @Size(max = 255) String email,
            // 72: limite do bcrypt; acima disso o resto da senha seria ignorado.
            @Size(min = 8, max = 72) String senha,
            @NotNull Papel papel,
            @Size(max = 200) String convite,
            /** Codigo do link de cadastro do grupo (opcional): a conta nasce pendente nesse grupo. */
            @Size(max = 200) String grupo) {}

    public record TrocarSenhaRequest(@NotBlank @Size(max = 128) String senhaAtual, @Size(min = 8, max = 72) String senhaNova) {}

    public record ConviteGestorRequest(@Email @NotBlank @Size(max = 255) String email) {}

    public record RecuperarSenhaRequest(@Email @NotBlank @Size(max = 255) String email) {}

    public record RedefinirSenhaRequest(@NotBlank @Size(max = 200) String token, @Size(min = 8, max = 72) String novaSenha) {}

    @PostMapping("/login")
    public ApiEnvelope<Map<String, Object>> login(@Valid @RequestBody LoginRequest requisicao) {
        AuthService.LoginResultado resultado = authService.login(requisicao.email(), requisicao.senha());
        return ApiEnvelope.sucesso(Map.of(
                "token", resultado.token(),
                "expira_em", resultado.expiraEm().toString(),
                "papel", resultado.papel(),
                "precisa_trocar_senha", resultado.precisaTrocarSenha()));
    }

    @GetMapping("/me")
    public ApiEnvelope<Map<String, Object>> me(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        GrupoService.Perfil perfil = grupoService.perfilDe(usuario);
        Map<String, Object> dados = new java.util.LinkedHashMap<>();
        dados.put("id", perfil.id().toString());
        dados.put("papel", perfil.papel());
        dados.put("nome", perfil.nome());
        dados.put("email", perfil.email());
        dados.put("status", perfil.status());
        dados.put("criado_em", perfil.criadoEm().toString());
        dados.put("grupos", perfil.grupos());
        dados.put("acesso_total", perfil.acessoTotal());
        return ApiEnvelope.sucesso(dados);
    }

    @PostMapping("/registro")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> registro(
            @Valid @RequestBody RegistroRequest requisicao, HttpServletRequest http) {
        if (!limitadorCadastro.consumir(origemDaRequisicao(http))) {
            throw new AppException(429, "Muitas tentativas de cadastro. Tente novamente mais tarde.");
        }
        boolean pelaLink = requisicao.grupo() != null && !requisicao.grupo().isBlank();
        // Link invalido ou substituido e recusado antes de criar qualquer conta.
        java.util.UUID grupoId = pelaLink ? grupoService.resolverLink(requisicao.grupo()) : null;
        Usuario usuario = authService.registrar(requisicao.nome(), requisicao.email(), requisicao.senha(),
                requisicao.papel(), requisicao.convite(), grupoId);
        Map<String, Object> dados = usuario.getStatus() == StatusConta.PENDENTE
                ? Map.of("mensagem", "Cadastro recebido, aguarde a confirmação.", "status", usuario.getStatus())
                : Map.of(
                        "id", usuario.getId().toString(),
                        "nome", usuario.getNome(),
                        "email", usuario.getEmail(),
                        "papel", usuario.getPapel());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.sucesso(dados));
    }

    /**
     * IP do cliente. Atras do proxy do Render usa a ultima entrada de X-Forwarded-For, a que o proprio
     * proxy acrescentou (as anteriores podem ser forjadas pelo cliente); sem o cabecalho, o IP da conexao.
     */
    private static String origemDaRequisicao(HttpServletRequest http) {
        String encaminhado = http.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            String[] entradas = encaminhado.split(",");
            return entradas[entradas.length - 1].trim();
        }
        return http.getRemoteAddr();
    }

    /** Convite de uso unico para um novo Admin Master (spec 024). Admins nascem por criacao direta, em POST /grupos. */
    @PostMapping("/convites-master")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> convidarMaster(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @RequestBody ConviteGestorRequest requisicao) {
        AuthService.ConviteCriado convite = authService.criarConviteMaster(usuario, requisicao.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.sucesso(Map.of(
                "link", convite.link(),
                "expira_em", convite.expiraEm().toString())));
    }

    /** Aceita tambem o token restrito do primeiro acesso (unica rota que ele alcanca). */
    @PostMapping("/trocar-senha")
    public ApiEnvelope<Map<String, Object>> trocarSenha(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @RequestBody TrocarSenhaRequest requisicao) {
        AuthService.SessaoRenovada sessao =
                authService.trocarSenha(usuario, requisicao.senhaAtual(), requisicao.senhaNova());
        return ApiEnvelope.sucesso(Map.of(
                "token", sessao.token(),
                "expira_em", sessao.expiraEm().toString(),
                "papel", sessao.papel()));
    }

    @PostMapping("/recuperar-senha")
    public ApiEnvelope<Map<String, String>> recuperarSenha(@Valid @RequestBody RecuperarSenhaRequest requisicao) {
        authService.solicitarRecuperacaoSenha(requisicao.email());
        return ApiEnvelope.sucesso(Map.of("mensagem",
                "Se o e-mail estiver cadastrado, o pedido foi registrado: produtor e agrônomo recebem uma senha temporária do "
                        + "administrador; o gestor recebe um link por e-mail."));
    }

    @PostMapping("/redefinir-senha")
    public ApiEnvelope<Map<String, String>> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest requisicao) {
        authService.redefinirSenha(requisicao.token(), requisicao.novaSenha());
        return ApiEnvelope.sucesso(Map.of("mensagem", "Senha redefinida com sucesso."));
    }
}
