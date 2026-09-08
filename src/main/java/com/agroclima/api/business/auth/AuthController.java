package com.agroclima.api.business.auth;

import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.security.UsuarioAutenticado;
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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String senha) {}

    public record RegistroRequest(
            @NotBlank String nome,
            @Email @NotBlank String email,
            @Size(min = 8) String senha,
            @NotNull Papel papel) {}

    public record RecuperarSenhaRequest(@Email @NotBlank String email) {}

    public record RedefinirSenhaRequest(@NotBlank String token, @Size(min = 8) String novaSenha) {}

    @PostMapping("/login")
    public ApiEnvelope<Map<String, Object>> login(@Valid @RequestBody LoginRequest requisicao) {
        AuthService.LoginResultado resultado = authService.login(requisicao.email(), requisicao.senha());
        return ApiEnvelope.sucesso(Map.of(
                "token", resultado.token(),
                "expira_em", resultado.expiraEm().toString(),
                "papel", resultado.papel()));
    }

    @GetMapping("/me")
    public ApiEnvelope<Map<String, Object>> me(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ApiEnvelope.sucesso(Map.of(
                "id", usuario.id().toString(),
                "papel", usuario.papel()));
    }

    @PostMapping("/registro")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> registro(@Valid @RequestBody RegistroRequest requisicao) {
        Usuario usuario = authService.registrar(
                requisicao.nome(), requisicao.email(), requisicao.senha(), requisicao.papel());
        Map<String, Object> dados = Map.of(
                "id", usuario.getId().toString(),
                "nome", usuario.getNome(),
                "email", usuario.getEmail(),
                "papel", usuario.getPapel());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.sucesso(dados));
    }

    @PostMapping("/recuperar-senha")
    public ApiEnvelope<Map<String, String>> recuperarSenha(@Valid @RequestBody RecuperarSenhaRequest requisicao) {
        authService.solicitarRecuperacaoSenha(requisicao.email());
        return ApiEnvelope.sucesso(Map.of("mensagem", "Se o email existir, um link de redefinição foi enviado."));
    }

    @PostMapping("/redefinir-senha")
    public ApiEnvelope<Map<String, String>> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest requisicao) {
        authService.redefinirSenha(requisicao.token(), requisicao.novaSenha());
        return ApiEnvelope.sucesso(Map.of("mensagem", "Senha redefinida com sucesso."));
    }
}
