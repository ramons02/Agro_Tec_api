package com.agroclima.api.business.mapa;

import com.agroclima.api.business.balancohidrico.BalancoHidricoDiario;
import com.agroclima.api.business.balancohidrico.BalancoHidricoDiarioRepository;
import com.agroclima.api.business.estacao.EstacaoInmetRepository;
import com.agroclima.api.business.estacao.FonteDados;
import com.agroclima.api.business.estacao.MedicaoClimaRepository;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.talhao.TalhaoRepository;
import com.agroclima.api.core.calculos.PulverizacaoCalculos;
import com.agroclima.api.core.calculos.StatusPlantio;
import com.agroclima.api.core.geo.GeoJsonUtil;
import com.agroclima.api.core.security.AutorizacaoService;
import com.agroclima.api.core.security.UsuarioAutenticado;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Espelha app/api/v1/endpoints/mapa.py. Propriedades escopadas por RBAC; estacoes NAO
 * (infraestrutura publica, igual ao Python). Sem paginacao -- Python tambem retorna tudo
 * de uma vez aqui; N+1 aceitavel no volume de dados do MVP (nao e o endpoint paginado
 * de RNF017, esse e o /dashboard/plantio).
 */
@Service
public class MapaService {

    private final PropriedadeRepository propriedadeRepository;
    private final TalhaoRepository talhaoRepository;
    private final BalancoHidricoDiarioRepository balancoHidricoDiarioRepository;
    private final EstacaoInmetRepository estacaoInmetRepository;
    private final MedicaoClimaRepository medicaoClimaRepository;
    private final AutorizacaoService autorizacaoService;

    public MapaService(
            PropriedadeRepository propriedadeRepository,
            TalhaoRepository talhaoRepository,
            BalancoHidricoDiarioRepository balancoHidricoDiarioRepository,
            EstacaoInmetRepository estacaoInmetRepository,
            MedicaoClimaRepository medicaoClimaRepository,
            AutorizacaoService autorizacaoService) {
        this.propriedadeRepository = propriedadeRepository;
        this.talhaoRepository = talhaoRepository;
        this.balancoHidricoDiarioRepository = balancoHidricoDiarioRepository;
        this.estacaoInmetRepository = estacaoInmetRepository;
        this.medicaoClimaRepository = medicaoClimaRepository;
        this.autorizacaoService = autorizacaoService;
    }

    public record TalhaoMapa(UUID id, String nome, JsonNode geometriaGeojson, StatusPlantio statusPlantio) {}

    public record PropriedadeMapa(UUID id, String nome, List<TalhaoMapa> talhoes) {}

    public record UltimaMedicao(double chuvaMm, double ventoKmh, FonteDados fonteDados) {}

    public record EstacaoMapa(String codigo, String nome, JsonNode posicaoGeojson, UltimaMedicao ultimaMedicao) {}

    public record DadosMapa(List<PropriedadeMapa> propriedades, List<EstacaoMapa> estacoes) {}

    @Transactional(readOnly = true)
    public DadosMapa obterDadosMapa(UsuarioAutenticado usuario) {
        Optional<List<UUID>> visiveis = autorizacaoService.propriedadeIdsVisiveis(usuario);
        List<Propriedade> propriedades = visiveis
                .map(ids -> ids.isEmpty() ? List.<Propriedade>of() : propriedadeRepository.findAllById(ids))
                .orElseGet(propriedadeRepository::findAll);

        List<PropriedadeMapa> propriedadesMapa = propriedades.stream().map(this::paraPropriedadeMapa).toList();
        List<EstacaoMapa> estacoesMapa = estacaoInmetRepository.findAll().stream().map(this::paraEstacaoMapa).toList();

        return new DadosMapa(propriedadesMapa, estacoesMapa);
    }

    private PropriedadeMapa paraPropriedadeMapa(Propriedade propriedade) {
        List<TalhaoMapa> talhoes = talhaoRepository.findByPropriedadeId(propriedade.getId(), Pageable.unpaged())
                .stream()
                .map(talhao -> new TalhaoMapa(
                        talhao.getId(),
                        talhao.getNome(),
                        GeoJsonUtil.paraGeoJsonNode(talhao.getGeometria()),
                        balancoHidricoDiarioRepository.findFirstByTalhaoIdOrderByDataDesc(talhao.getId())
                                .map(BalancoHidricoDiario::getStatusPlantio)
                                .orElse(null)))
                .toList();
        return new PropriedadeMapa(propriedade.getId(), propriedade.getNome(), talhoes);
    }

    private EstacaoMapa paraEstacaoMapa(com.agroclima.api.business.estacao.EstacaoInmet estacao) {
        UltimaMedicao ultima = medicaoClimaRepository.findFirstByEstacaoCodigoOrderByDataHoraUtcDesc(estacao.getCodigo())
                .map(medicao -> new UltimaMedicao(
                        medicao.getPrecipitacaoMm() != null ? medicao.getPrecipitacaoMm() : 0.0,
                        medicao.getVentoVelocidadeMs() != null
                                ? PulverizacaoCalculos.converterMsParaKmh(medicao.getVentoVelocidadeMs())
                                : 0.0,
                        medicao.getFonteDados()))
                .orElse(null);
        return new EstacaoMapa(
                estacao.getCodigo(), estacao.getNome(), GeoJsonUtil.paraGeoJsonNode(estacao.getPosicao()), ultima);
    }
}
