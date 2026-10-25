package com.agroclima.api.business.propriedade;

import com.agroclima.api.business.grupo.Grupo;
import com.agroclima.api.business.grupo.GrupoRepository;
import com.agroclima.api.core.geo.GeoJsonUtil;
import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.response.PagedResponse;
import com.agroclima.api.core.security.UsuarioAutenticado;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Espelha app/api/v1/endpoints/propriedades.py. */
@RestController
@RequestMapping("/api/v1/propriedades")
public class PropriedadeController {

    private static final int TAMANHO_PAGINA_PADRAO = 20;

    private final PropriedadeService propriedadeService;
    private final GrupoRepository grupoRepository;

    public PropriedadeController(PropriedadeService propriedadeService, GrupoRepository grupoRepository) {
        this.propriedadeService = propriedadeService;
        this.grupoRepository = grupoRepository;
    }

    // 100: tamanho da coluna (V3 e V11); acima disso o banco recusava e a API respondia 500.
    public record PropriedadeRequest(@NotBlank @Size(max = 100) String nome, @Size(max = 100) String municipio, JsonNode geometria) {}

    @PostMapping
    public ApiEnvelope<Map<String, Object>> criar(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @RequestBody PropriedadeRequest requisicao) {
        Propriedade propriedade =
                propriedadeService.criar(usuario, requisicao.nome(), requisicao.municipio(), requisicao.geometria());
        return ApiEnvelope.sucesso(paraDados(propriedade, nomesDosGrupos(List.of(propriedade))));
    }

    @GetMapping
    public ApiEnvelope<PagedResponse<Map<String, Object>>> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "" + TAMANHO_PAGINA_PADRAO) int pageSize) {
        Page<Propriedade> pagina = propriedadeService.listar(usuario, page, pageSize);
        Map<UUID, String> nomes = nomesDosGrupos(pagina.getContent());
        var itens = pagina.getContent().stream().map(p -> paraDados(p, nomes)).toList();
        return ApiEnvelope.sucesso(new PagedResponse<>(itens, pagina.getTotalElements(), page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiEnvelope<Map<String, Object>> buscar(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        Propriedade propriedade = propriedadeService.buscarVisivelOu404(usuario, id);
        return ApiEnvelope.sucesso(paraDados(propriedade, nomesDosGrupos(List.of(propriedade))));
    }

    @PutMapping("/{id}")
    public ApiEnvelope<Map<String, Object>> atualizar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID id,
            @Valid @RequestBody PropriedadeRequest requisicao) {
        Propriedade propriedade = propriedadeService.atualizar(
                usuario, id, requisicao.nome(), requisicao.municipio(), requisicao.geometria());
        return ApiEnvelope.sucesso(paraDados(propriedade, nomesDosGrupos(List.of(propriedade))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        propriedadeService.excluir(usuario, id);
        return ResponseEntity.noContent().build();
    }

    /** Nome de cada grupo das propriedades, numa consulta so (a lista nao pode fazer uma consulta por linha). */
    private Map<UUID, String> nomesDosGrupos(Collection<Propriedade> propriedades) {
        List<UUID> ids = propriedades.stream().map(Propriedade::getGrupoId).filter(java.util.Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : grupoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Grupo::getId, Grupo::getNome));
    }

    private Map<String, Object> paraDados(Propriedade propriedade, Map<UUID, String> nomesDosGrupos) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("id", propriedade.getId().toString());
        dados.put("nome", propriedade.getNome());
        dados.put("municipio", propriedade.getMunicipio());
        dados.put("proprietario_id", propriedade.getProprietarioId().toString());
        dados.put("geometria", GeoJsonUtil.paraGeoJsonNode(propriedade.getGeometria()));
        // O app mostra o grupo so para o Master; os outros perfis so recebem o proprio grupo, que ja e deles.
        UUID grupoId = propriedade.getGrupoId();
        dados.put("grupo", grupoId == null ? null : Map.of("id", grupoId.toString(),
                "nome", nomesDosGrupos.getOrDefault(grupoId, "")));
        return dados;
    }
}
