package com.agroclima.api.business.talhao;

import com.agroclima.api.core.geo.GeoJsonUtil;
import com.agroclima.api.core.geo.GeometriaInvalidaException;
import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.response.PagedResponse;
import com.agroclima.api.core.security.UsuarioAutenticado;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.locationtech.jts.geom.Geometry;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Espelha app/api/v1/endpoints/talhoes.py -- so os endpoints da Fase 5 (criacao, import,
 * listagem, busca, exclusao). estacao-mais-proxima/pulverizacao/recomendacao/balanco-hidrico
 * dependem de business.clima/balancohidrico (Fase 6/7), ficam pra la.
 */
@RestController
@RequestMapping("/api/v1/talhoes")
public class TalhaoController {

    private static final int TAMANHO_PAGINA_PADRAO = 20;

    private final TalhaoService talhaoService;

    public TalhaoController(TalhaoService talhaoService) {
        this.talhaoService = talhaoService;
    }

    public record TalhaoRequest(
            @NotNull UUID propriedadeId,
            @NotBlank String nome,
            @NotNull JsonNode geometria,
            boolean confirmarForaDoPara) {}

    @PostMapping
    public ApiEnvelope<Map<String, Object>> criar(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @RequestBody TalhaoRequest requisicao) {
        Geometry geometria = GeoJsonUtil.parseGeometria(requisicao.geometria().toString());
        var resultado = talhaoService.criarDeGeometria(
                usuario, requisicao.propriedadeId(), requisicao.nome(), geometria, requisicao.confirmarForaDoPara());
        return ApiEnvelope.sucesso(paraDadosComAviso(resultado));
    }

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiEnvelope<Map<String, Object>> importar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam("propriedade_id") UUID propriedadeId,
            @RequestParam("nome") String nome,
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(name = "confirmar_fora_do_para", defaultValue = "false") boolean confirmarForaDoPara) {
        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        String nomeArquivo = arquivo.getOriginalFilename();
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            throw new GeometriaInvalidaException("Arquivo sem nome.");
        }
        var resultado = talhaoService.criarDeArquivo(
                usuario, propriedadeId, nome, nomeArquivo, conteudo, confirmarForaDoPara);
        return ApiEnvelope.sucesso(paraDadosComAviso(resultado));
    }

    @GetMapping
    public ApiEnvelope<PagedResponse<Map<String, Object>>> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(name = "propriedade_id", required = false) UUID propriedadeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "" + TAMANHO_PAGINA_PADRAO) int pageSize) {
        Page<Talhao> pagina = talhaoService.listar(usuario, propriedadeId, page, pageSize);
        var itens = pagina.getContent().stream().map(this::paraDados).toList();
        return ApiEnvelope.sucesso(new PagedResponse<>(itens, pagina.getTotalElements(), page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiEnvelope<Map<String, Object>> buscar(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        return ApiEnvelope.sucesso(paraDados(talhaoService.buscarVisivelOu404(usuario, id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        talhaoService.excluir(usuario, id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> paraDadosComAviso(TalhaoService.ResultadoCriacao resultado) {
        Map<String, Object> dados = paraDados(resultado.talhao());
        dados.put("aviso", resultado.aviso());
        return dados;
    }

    private Map<String, Object> paraDados(Talhao talhao) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("id", talhao.getId().toString());
        dados.put("propriedade_id", talhao.getPropriedadeId().toString());
        dados.put("nome", talhao.getNome());
        dados.put("geometria", GeoJsonUtil.paraGeoJsonNode(talhao.getGeometria()));
        dados.put("area_ha", talhao.getAreaHa());
        dados.put("tipo_solo", talhao.getTipoSolo());
        dados.put("capacidade_agua_disponivel_mm", talhao.getCapacidadeAguaDisponivelMm());
        return dados;
    }
}
