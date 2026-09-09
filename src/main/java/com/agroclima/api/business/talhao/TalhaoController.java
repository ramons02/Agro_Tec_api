package com.agroclima.api.business.talhao;

import com.agroclima.api.business.balancohidrico.BalancoHidricoDiario;
import com.agroclima.api.business.balancohidrico.BalancoHidricoDiarioRepository;
import com.agroclima.api.business.clima.ClimaTempoRealService;
import com.agroclima.api.business.estacao.EstacaoInmetRepository;
import com.agroclima.api.business.estacao.EstacaoProximaProjecao;
import com.agroclima.api.core.calculos.ClassificacaoPulverizacao;
import com.agroclima.api.core.calculos.PsicrometriaCalculos;
import com.agroclima.api.core.calculos.PulverizacaoCalculos;
import com.agroclima.api.core.calculos.RecomendacaoCalculos;
import com.agroclima.api.core.calculos.StatusPlantio;
import com.agroclima.api.core.calculos.TendenciaUmidade;
import com.agroclima.api.core.geo.GeoJsonUtil;
import com.agroclima.api.core.geo.GeometriaInvalidaException;
import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.response.AppException;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Espelha app/api/v1/endpoints/talhoes.py -- criacao/import/listagem/busca/exclusao
 * (Fase 5), estacao-mais-proxima/pulverizacao (Fase 6) e recomendacao/balanco-hidrico
 * (Fase 7, dependiam de business.balancohidrico).
 */
@RestController
@RequestMapping("/api/v1/talhoes")
public class TalhaoController {

    private static final int TAMANHO_PAGINA_PADRAO = 20;

    private final TalhaoService talhaoService;
    private final EstacaoInmetRepository estacaoInmetRepository;
    private final ClimaTempoRealService climaTempoRealService;
    private final BalancoHidricoDiarioRepository balancoHidricoDiarioRepository;

    public TalhaoController(
            TalhaoService talhaoService,
            EstacaoInmetRepository estacaoInmetRepository,
            ClimaTempoRealService climaTempoRealService,
            BalancoHidricoDiarioRepository balancoHidricoDiarioRepository) {
        this.talhaoService = talhaoService;
        this.estacaoInmetRepository = estacaoInmetRepository;
        this.climaTempoRealService = climaTempoRealService;
        this.balancoHidricoDiarioRepository = balancoHidricoDiarioRepository;
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

    @GetMapping("/{id}/estacao-mais-proxima")
    public ApiEnvelope<Map<String, Object>> estacaoMaisProxima(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        Talhao talhao = talhaoService.buscarVisivelOu404(usuario, id);
        List<EstacaoProximaProjecao> estacoes =
                estacaoInmetRepository.buscarMaisProximas(talhao.getGeometria().getCentroid(), 3);
        if (estacoes.isEmpty()) {
            throw new AppException(404, "Nenhuma estação disponível.");
        }
        List<Map<String, Object>> itens = estacoes.stream()
                .map(e -> (Map<String, Object>) Map.<String, Object>of(
                        "estacao_codigo", e.getCodigo(),
                        "nome", e.getNome(),
                        "distancia_km", e.getDistanciaKm(),
                        "latitude", e.getLatitude(),
                        "longitude", e.getLongitude()))
                .toList();
        return ApiEnvelope.sucesso(Map.of("estacoes", itens));
    }

    @GetMapping("/{id}/pulverizacao")
    public ApiEnvelope<Map<String, Object>> pulverizacao(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        Talhao talhao = talhaoService.buscarVisivelOu404(usuario, id);
        ResultadoPulverizacaoInterno resultado = classificarPulverizacaoAtual(talhao)
                .orElseThrow(() -> new AppException(404, "Nenhuma leitura de vento disponível."));

        Map<String, Object> dados = new HashMap<>();
        dados.put("classificacao", resultado.classificacaoFinal());
        dados.put("motivos_bloqueio", resultado.motivosBloqueio());
        dados.put("vento_kmh", resultado.ventoKmh());
        dados.put("rajada_kmh", resultado.rajadaKmh());
        dados.put("delta_t_c", resultado.deltaTC());
        dados.put("fonte_dados", resultado.fonteDados());
        return ApiEnvelope.sucesso(dados);
    }

    @GetMapping("/{id}/recomendacao")
    public ApiEnvelope<Map<String, Object>> recomendacao(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        Talhao talhao = talhaoService.buscarVisivelOu404(usuario, id);

        Optional<BalancoHidricoDiario> balancoOpt = balancoHidricoDiarioRepository.findFirstByTalhaoIdOrderByDataDesc(id);
        StatusPlantio statusPlantio = balancoOpt.map(BalancoHidricoDiario::getStatusPlantio).orElse(null);

        TendenciaUmidade tendencia = TendenciaUmidade.ESTAVEL;
        if (statusPlantio == StatusPlantio.AMARELO && talhao.getCapacidadeAguaDisponivelMm() != null) {
            BalancoHidricoDiario hoje = balancoOpt.get();
            Optional<BalancoHidricoDiario> tresDiasAtras =
                    balancoHidricoDiarioRepository.findByTalhaoIdAndData(id, hoje.getData().minusDays(3));
            if (tresDiasAtras.isPresent()) {
                tendencia = RecomendacaoCalculos.calcularTendenciaUmidade(
                        hoje.getArmazenamentoMm(), tresDiasAtras.get().getArmazenamentoMm(),
                        talhao.getCapacidadeAguaDisponivelMm());
            }
        }

        ClassificacaoPulverizacao classificacaoPulverizacao = classificarPulverizacaoAtual(talhao)
                .map(ResultadoPulverizacaoInterno::classificacaoFinal)
                .orElse(null);

        RecomendacaoCalculos.Recomendacao recomendacao =
                RecomendacaoCalculos.gerarRecomendacao(statusPlantio, classificacaoPulverizacao, tendencia);

        Map<String, Object> dados = new HashMap<>();
        dados.put("texto", recomendacao.texto());
        dados.put("prioridade", recomendacao.prioridade());
        dados.put("aviso", recomendacao.aviso());
        return ApiEnvelope.sucesso(dados);
    }

    @GetMapping("/{id}/balanco-hidrico")
    public ApiEnvelope<Map<String, Object>> balancoHidrico(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        Talhao talhao = talhaoService.buscarVisivelOu404(usuario, id);
        BalancoHidricoDiario balanco = balancoHidricoDiarioRepository.findFirstByTalhaoIdOrderByDataDesc(id)
                .orElseThrow(() -> new AppException(404, "Balanço hídrico ainda não calculado para este talhão."));

        double cad = talhao.getCapacidadeAguaDisponivelMm() != null ? talhao.getCapacidadeAguaDisponivelMm() : 0.0;
        double percentualCad = cad > 0 ? balanco.getArmazenamentoMm() / cad * 100.0 : 0.0;

        Map<String, Object> dados = new HashMap<>();
        dados.put("data", balanco.getData().toString());
        dados.put("armazenamento_mm", balanco.getArmazenamentoMm());
        dados.put("cad_mm", cad);
        dados.put("percentual_cad", percentualCad);
        return ApiEnvelope.sucesso(dados);
    }

    private record ResultadoPulverizacaoInterno(
            ClassificacaoPulverizacao classificacaoFinal,
            List<String> motivosBloqueio,
            double ventoKmh,
            Double rajadaKmh,
            Double deltaTC,
            Object fonteDados) {}

    private Optional<ResultadoPulverizacaoInterno> classificarPulverizacaoAtual(Talhao talhao) {
        return climaTempoRealService.obterClimaAtual(talhao).map(resultado -> {
            ClassificacaoPulverizacao classificacaoVento =
                    PulverizacaoCalculos.classificarPulverizacao(resultado.ventoKmh(), resultado.rajadaKmh());

            Double deltaT = null;
            ClassificacaoPulverizacao classificacaoDeltaT = ClassificacaoPulverizacao.FAVORAVEL;
            if (resultado.temperaturaC() != null && resultado.umidadePct() != null) {
                deltaT = PsicrometriaCalculos.calcularDeltaT(resultado.temperaturaC(), resultado.umidadePct());
                classificacaoDeltaT = PulverizacaoCalculos.classificarDeltaT(deltaT);
            }

            List<String> motivosBloqueio = new ArrayList<>();
            if (classificacaoVento != ClassificacaoPulverizacao.FAVORAVEL) {
                motivosBloqueio.add(classificacaoVento.name());
            }
            if (classificacaoDeltaT != ClassificacaoPulverizacao.FAVORAVEL) {
                motivosBloqueio.add(classificacaoDeltaT.name());
            }
            ClassificacaoPulverizacao classificacaoFinal = classificacaoVento != ClassificacaoPulverizacao.FAVORAVEL
                    ? classificacaoVento
                    : classificacaoDeltaT;

            return new ResultadoPulverizacaoInterno(
                    classificacaoFinal, motivosBloqueio, resultado.ventoKmh(), resultado.rajadaKmh(), deltaT,
                    resultado.fonteDados());
        });
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
