package com.agroclima.api.business.grupo;

import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Grupos (tenants) e mudancas de grupo (spec 024). Ver contracts/hierarquia.md. */
@RestController
@RequestMapping("/api/v1")
public class GrupoController {

    private final GrupoService grupoService;

    public GrupoController(GrupoService grupoService) {
        this.grupoService = grupoService;
    }

    public record NovoGrupoRequest(
            @NotBlank @Size(max = 120) String nome,
            @NotBlank @Size(max = 255) String adminNome,
            @Email @NotBlank String adminEmail) {}

    public record MoverRequest(@NotNull UUID grupoId, UUID grupoOrigemId) {}

    public record ReatribuirRequest(@NotNull UUID grupoDestinoId) {}

    @GetMapping("/grupos")
    public ApiEnvelope<Map<String, List<GrupoService.ItemGrupo>>> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ApiEnvelope.sucesso(Map.of("itens", grupoService.listar(usuario)));
    }

    /** Cria o grupo e o Admin dele; a senha temporaria do Admin vem so nesta resposta. */
    @PostMapping("/grupos")
    public ResponseEntity<ApiEnvelope<GrupoService.GrupoCriado>> criar(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @RequestBody NovoGrupoRequest requisicao) {
        GrupoService.GrupoCriado criado =
                grupoService.criar(usuario, requisicao.nome(), requisicao.adminNome(), requisicao.adminEmail());
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(ApiEnvelope.sucesso(criado));
    }

    @PostMapping("/usuarios/{id}/mover")
    public ApiEnvelope<Map<String, String>> mover(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID id,
            @Valid @RequestBody MoverRequest requisicao) {
        grupoService.mover(usuario, id, requisicao.grupoId(), requisicao.grupoOrigemId());
        return ApiEnvelope.sucesso(Map.of("id", id.toString(), "grupo_id", requisicao.grupoId().toString()));
    }

    @PostMapping("/grupos/{id}/reatribuir")
    public ApiEnvelope<GrupoService.ResultadoReatribuicao> reatribuir(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID id,
            @Valid @RequestBody ReatribuirRequest requisicao) {
        return ApiEnvelope.sucesso(grupoService.reatribuir(usuario, id, requisicao.grupoDestinoId()));
    }

    @DeleteMapping("/grupos/{id}/agronomos/{agronomoId}")
    public ResponseEntity<Void> removerAgronomo(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id, @PathVariable UUID agronomoId) {
        grupoService.removerAgronomo(usuario, id, agronomoId);
        return ResponseEntity.noContent().build();
    }
}
