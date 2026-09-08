package com.agroclima.api.business.dashboard;

import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.response.PagedResponse;
import com.agroclima.api.core.security.UsuarioAutenticado;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Espelha app/api/v1/endpoints/dashboard.py. */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private static final int TAMANHO_PAGINA_PADRAO = 20;

    private final DashboardService dashboardService;
    private final ExportacaoCsvService exportacaoCsvService;

    public DashboardController(DashboardService dashboardService, ExportacaoCsvService exportacaoCsvService) {
        this.dashboardService = dashboardService;
        this.exportacaoCsvService = exportacaoCsvService;
    }

    @GetMapping("/plantio")
    public ApiEnvelope<PagedResponse<Map<String, Object>>> plantio(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(name = "propriedade_id", required = false) UUID propriedadeId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "" + TAMANHO_PAGINA_PADRAO) int pageSize) {
        Page<DashboardItemProjecao> pagina = dashboardService.listar(usuario, propriedadeId, status, page, pageSize);
        var itens = pagina.getContent().stream().map(this::paraDados).toList();
        return ApiEnvelope.sucesso(new PagedResponse<>(itens, pagina.getTotalElements(), page, pageSize));
    }

    @GetMapping("/plantio/exportar.csv")
    public ResponseEntity<byte[]> exportarCsv(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(name = "propriedade_id", required = false) UUID propriedadeId,
            @RequestParam(required = false) String status) {
        var itens = dashboardService.listarTudo(usuario, propriedadeId, status);
        String csv = exportacaoCsvService.gerarCsvTalhoes(itens);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"talhoes.csv\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> paraDados(DashboardItemProjecao item) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("talhao_id", item.getTalhaoId().toString());
        dados.put("nome", item.getNome());
        dados.put("propriedade", item.getPropriedadeNome());
        dados.put("area_ha", item.getAreaHa());
        dados.put("tipo_solo", item.getTipoSolo());
        dados.put("status_plantio", item.getStatusPlantio());
        dados.put("armazenamento_mm", item.getArmazenamentoMm());
        Double percentualCad = (item.getArmazenamentoMm() != null && item.getCadMm() != null && item.getCadMm() > 0)
                ? item.getArmazenamentoMm() / item.getCadMm() * 100.0
                : null;
        dados.put("percentual_cad", percentualCad);
        return dados;
    }
}
