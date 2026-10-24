package com.agroclima.api.business.mapa;

import com.agroclima.api.business.balancohidrico.BalancoHidricoDiario;
import com.agroclima.api.business.balancohidrico.BalancoHidricoDiarioRepository;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.talhao.TalhaoRepository;
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
 * Espelha app/api/v1/endpoints/mapa.py. Propriedades escopadas por RBAC. Sem estacoes: o clima
 * vem de modelo, por talhao. Sem paginacao -- Python tambem retorna tudo
 * de uma vez aqui; N+1 aceitavel no volume de dados do MVP (nao e o endpoint paginado
 * de RNF017, esse e o /dashboard/plantio).
 */
@Service
public class MapaService {

    private final PropriedadeRepository propriedadeRepository;
    private final TalhaoRepository talhaoRepository;
    private final BalancoHidricoDiarioRepository balancoHidricoDiarioRepository;
    private final AutorizacaoService autorizacaoService;

    public MapaService(
            PropriedadeRepository propriedadeRepository,
            TalhaoRepository talhaoRepository,
            BalancoHidricoDiarioRepository balancoHidricoDiarioRepository,
            AutorizacaoService autorizacaoService) {
        this.propriedadeRepository = propriedadeRepository;
        this.talhaoRepository = talhaoRepository;
        this.balancoHidricoDiarioRepository = balancoHidricoDiarioRepository;
        this.autorizacaoService = autorizacaoService;
    }

    public record TalhaoMapa(UUID id, String nome, JsonNode geometriaGeojson, StatusPlantio statusPlantio) {}

    public record PropriedadeMapa(UUID id, String nome, List<TalhaoMapa> talhoes) {}

    public record DadosMapa(List<PropriedadeMapa> propriedades) {}

    @Transactional(readOnly = true)
    public DadosMapa obterDadosMapa(UsuarioAutenticado usuario) {
        Optional<List<UUID>> visiveis = autorizacaoService.propriedadeIdsVisiveis(usuario);
        List<Propriedade> propriedades = visiveis
                .map(ids -> ids.isEmpty() ? List.<Propriedade>of() : propriedadeRepository.findAllById(ids))
                .orElseGet(propriedadeRepository::findAll);

        List<PropriedadeMapa> propriedadesMapa = propriedades.stream().map(this::paraPropriedadeMapa).toList();

        return new DadosMapa(propriedadesMapa);
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
}
