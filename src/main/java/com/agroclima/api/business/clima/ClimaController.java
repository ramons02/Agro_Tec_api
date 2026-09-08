package com.agroclima.api.business.clima;

import com.agroclima.api.business.talhao.Talhao;
import com.agroclima.api.business.talhao.TalhaoRepository;
import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.response.AppException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Espelha app/api/v1/endpoints/clima.py -- sem escopo de RBAC aqui de proposito (qualquer
 * usuario autenticado pode consultar qualquer talhao_id), igual ao Python. Headers de
 * nao-cache sempre presentes (RN008/RN017 -- nunca servir >30min stale sem tentar refresh).
 */
@RestController
@RequestMapping("/api/v1/clima")
public class ClimaController {

    private final TalhaoRepository talhaoRepository;
    private final ClimaTempoRealService climaTempoRealService;

    public ClimaController(TalhaoRepository talhaoRepository, ClimaTempoRealService climaTempoRealService) {
        this.talhaoRepository = talhaoRepository;
        this.climaTempoRealService = climaTempoRealService;
    }

    @GetMapping("/atual")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> climaAtual(
            @RequestParam("talhao_id") UUID talhaoId, @RequestParam(name = "_t", required = false) Long cacheBuster) {
        Talhao talhao = talhaoRepository.findById(talhaoId)
                .orElseThrow(() -> new AppException(404, "Talhão não encontrado."));

        ClimaTempoRealService.ClimaAtualResultado resultado = climaTempoRealService.obterClimaAtual(talhao)
                .orElseThrow(() -> new AppException(404, "Nenhuma medição disponível para este talhão ainda."));

        Map<String, Object> dados = new java.util.HashMap<>();
        dados.put("estacao", resultado.estacaoCodigo());
        dados.put("chuva_mm", resultado.chuvaMm());
        dados.put("vento_kmh", resultado.ventoKmh());
        dados.put("rajada_kmh", resultado.rajadaKmh());
        dados.put("temperatura_c", resultado.temperaturaC());
        dados.put("umidade_pct", resultado.umidadePct());
        dados.put("fonte_dados", resultado.fonteDados());
        dados.put("medido_em_utc", resultado.medidoEmUtc().toString());

        // Header literal (nao via CacheControl builder) -- CacheControl.noCache().noStore()
        // omite "no-cache" do resultado quando noStore tambem esta setado; queremos os
        // 3 tokens explicitos, igual ao header cru que o Python envia.
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(ApiEnvelope.sucesso(dados));
    }
}
