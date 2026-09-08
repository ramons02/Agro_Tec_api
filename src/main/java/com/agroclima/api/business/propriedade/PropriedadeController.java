package com.agroclima.api.business.propriedade;

import com.agroclima.api.core.geo.GeoJsonUtil;
import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.response.PagedResponse;
import com.agroclima.api.core.security.UsuarioAutenticado;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Espelha app/api/v1/endpoints/propriedades.py. */
@RestController
@RequestMapping("/api/v1/propriedades")
public class PropriedadeController {

    private static final int TAMANHO_PAGINA_PADRAO = 20;

    private final PropriedadeService propriedadeService;

    public PropriedadeController(PropriedadeService propriedadeService) {
        this.propriedadeService = propriedadeService;
    }

    public record PropriedadeRequest(@NotBlank String nome, String municipio, JsonNode geometria) {}

    @PostMapping
    public ApiEnvelope<Map<String, Object>> criar(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @RequestBody PropriedadeRequest requisicao) {
        Propriedade propriedade =
                propriedadeService.criar(usuario, requisicao.nome(), requisicao.municipio(), requisicao.geometria());
        return ApiEnvelope.sucesso(paraDados(propriedade));
    }

    @GetMapping
    public ApiEnvelope<PagedResponse<Map<String, Object>>> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "" + TAMANHO_PAGINA_PADRAO) int pageSize) {
        Page<Propriedade> pagina = propriedadeService.listar(usuario, page, pageSize);
        var itens = pagina.getContent().stream().map(this::paraDados).toList();
        return ApiEnvelope.sucesso(new PagedResponse<>(itens, pagina.getTotalElements(), page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiEnvelope<Map<String, Object>> buscar(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        return ApiEnvelope.sucesso(paraDados(propriedadeService.buscarVisivelOu404(usuario, id)));
    }

    @PutMapping("/{id}")
    public ApiEnvelope<Map<String, Object>> atualizar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID id,
            @Valid @RequestBody PropriedadeRequest requisicao) {
        Propriedade propriedade = propriedadeService.atualizar(
                usuario, id, requisicao.nome(), requisicao.municipio(), requisicao.geometria());
        return ApiEnvelope.sucesso(paraDados(propriedade));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        propriedadeService.excluir(usuario, id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> paraDados(Propriedade propriedade) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("id", propriedade.getId().toString());
        dados.put("nome", propriedade.getNome());
        dados.put("municipio", propriedade.getMunicipio());
        dados.put("proprietario_id", propriedade.getProprietarioId().toString());
        dados.put("geometria", GeoJsonUtil.paraGeoJsonNode(propriedade.getGeometria()));
        return dados;
    }
}
