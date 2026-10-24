package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.StatusConta;
import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.response.PagedResponse;
import com.agroclima.api.core.security.UsuarioAutenticado;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.agroclima.api.business.auth.Usuario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/** Monitoramento de contas (so gestor de tecnologia): quem esta criando conta e como usa o sistema. */
@RestController
@RequestMapping("/api/v1/usuarios")
public class GestaoUsuariosController {

    private static final int TAMANHO_PAGINA_PADRAO = 20;

    private final GestaoUsuariosService gestaoUsuariosService;

    public GestaoUsuariosController(GestaoUsuariosService gestaoUsuariosService) {
        this.gestaoUsuariosService = gestaoUsuariosService;
    }

    /** Mais recentes primeiro; filtro opcional por papel e busca por nome ou e-mail. */
    @GetMapping
    public ApiEnvelope<PagedResponse<GestaoUsuariosService.ItemUsuario>> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required = false) Papel papel,
            @RequestParam(required = false) StatusConta status,
            @RequestParam(name = "esqueceu_senha", defaultValue = "false") boolean esqueceuSenha,
            @RequestParam(name = "grupo_id", required = false) UUID grupoId,
            @RequestParam(name = "q", required = false) String busca,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "" + TAMANHO_PAGINA_PADRAO) int pageSize) {
        Page<GestaoUsuariosService.ItemUsuario> pagina =
                gestaoUsuariosService.listar(usuario, papel, status, esqueceuSenha, grupoId, busca, page, pageSize);
        return ApiEnvelope.sucesso(new PagedResponse<>(
                pagina.getContent(), pagina.getTotalElements(), page, pagina.getPageable().getPageSize()));
    }

    @GetMapping("/resumo")
    public ApiEnvelope<GestaoUsuariosService.Resumo> resumo(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ApiEnvelope.sucesso(gestaoUsuariosService.resumir(usuario));
    }

    /** Confirma o cadastro pendente; a resposta traz a senha temporaria, que nunca mais aparece. */
    @PostMapping("/{id}/confirmar")
    public ResponseEntity<ApiEnvelope<GestaoUsuariosService.SenhaTemporariaGerada>> confirmar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmarRequest requisicao) {
        UUID grupoId = requisicao == null ? null : requisicao.grupoId();
        return semCache(ApiEnvelope.sucesso(gestaoUsuariosService.confirmar(usuario, id, grupoId)));
    }

    /** Recusar apaga o cadastro pendente. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> recusar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        gestaoUsuariosService.recusar(usuario, id);
        return ResponseEntity.noContent().build();
    }

    public record NovoUsuarioRequest(
            @NotBlank @Size(max = 255) String nome,
            @Email @NotBlank String email,
            @NotNull Papel papel,
            UUID grupoId) {}

    public record ConfirmarRequest(UUID grupoId) {}

    public record EditarRequest(@Size(max = 255) String nome, @Email String email) {}

    @GetMapping("/{id}")
    public ApiEnvelope<GestaoUsuariosService.DetalheUsuario> detalhar(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        return ApiEnvelope.sucesso(gestaoUsuariosService.detalhar(usuario, id));
    }

    /** Cria produtor ou agronomo ja ATIVO; a resposta traz a senha temporaria, que nunca mais aparece. */
    @PostMapping
    public ResponseEntity<ApiEnvelope<GestaoUsuariosService.SenhaTemporariaGerada>> criar(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @RequestBody NovoUsuarioRequest requisicao) {
        var gerada = gestaoUsuariosService.criar(
                usuario, requisicao.nome(), requisicao.email(), requisicao.papel(), requisicao.grupoId());
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(ApiEnvelope.sucesso(gerada));
    }

    @PostMapping("/{id}/redefinir-senha")
    public ResponseEntity<ApiEnvelope<GestaoUsuariosService.SenhaTemporariaGerada>> redefinirSenha(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        return semCache(ApiEnvelope.sucesso(gestaoUsuariosService.redefinirSenha(usuario, id)));
    }

    @PatchMapping("/{id}")
    public ApiEnvelope<Map<String, Object>> editar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID id,
            @Valid @RequestBody EditarRequest requisicao) {
        Usuario editado = gestaoUsuariosService.editar(usuario, id, requisicao.nome(), requisicao.email());
        return ApiEnvelope.sucesso(Map.of(
                "id", editado.getId().toString(),
                "nome", editado.getNome() == null ? "" : editado.getNome(),
                "email", editado.getEmail()));
    }

    public record SuspenderRequest(@Size(max = 500) String motivo) {}

    @PostMapping("/{id}/suspender")
    public ApiEnvelope<Map<String, Object>> suspender(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid SuspenderRequest requisicao) {
        gestaoUsuariosService.suspender(usuario, id, requisicao == null ? null : requisicao.motivo());
        return ApiEnvelope.sucesso(Map.of("id", id.toString(), "status", StatusConta.SUSPENSA));
    }

    @PostMapping("/{id}/reativar")
    public ApiEnvelope<Map<String, Object>> reativar(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        gestaoUsuariosService.reativar(usuario, id);
        return ApiEnvelope.sucesso(Map.of("id", id.toString(), "status", StatusConta.ATIVA));
    }

    /** Respostas com senha temporaria nunca podem ficar em cache do navegador nem de intermediarios. */
    private static <T> ResponseEntity<T> semCache(T corpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(corpo);
    }
}
