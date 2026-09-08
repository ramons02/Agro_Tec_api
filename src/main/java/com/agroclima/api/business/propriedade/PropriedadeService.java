package com.agroclima.api.business.propriedade;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.core.geo.GeoJsonUtil;
import com.agroclima.api.core.geo.GeometriaUtil;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.security.AutorizacaoService;
import com.agroclima.api.core.security.UsuarioAutenticado;
import com.fasterxml.jackson.databind.JsonNode;
import org.locationtech.jts.geom.MultiPolygon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Espelha app/api/v1/endpoints/propriedades.py. */
@Service
public class PropriedadeService {

    private final PropriedadeRepository propriedadeRepository;
    private final AutorizacaoService autorizacaoService;

    public PropriedadeService(PropriedadeRepository propriedadeRepository, AutorizacaoService autorizacaoService) {
        this.propriedadeRepository = propriedadeRepository;
        this.autorizacaoService = autorizacaoService;
    }

    @Transactional
    public Propriedade criar(UsuarioAutenticado usuario, String nome, String municipio, JsonNode geometriaNode) {
        if (usuario.papel() == Papel.AGRONOMO) {
            throw new AppException(403, "Agrônomos têm acesso somente leitura.", Map.of("papel", usuario.papel()));
        }
        MultiPolygon geometria = converterGeometria(geometriaNode);
        return propriedadeRepository.save(new Propriedade(nome, municipio, usuario.id(), geometria));
    }

    @Transactional(readOnly = true)
    public Page<Propriedade> listar(UsuarioAutenticado usuario, int page, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), pageSize);
        Optional<List<UUID>> visiveis = autorizacaoService.propriedadeIdsVisiveis(usuario);
        return visiveis
                .map(ids -> propriedadeRepository.findByIdIn(ids, pageable))
                .orElseGet(() -> propriedadeRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    public Propriedade buscarVisivelOu404(UsuarioAutenticado usuario, UUID id) {
        verificarVisivelOu404(usuario, id);
        return propriedadeRepository.findById(id)
                .orElseThrow(() -> new AppException(404, "Propriedade não encontrada."));
    }

    @Transactional
    public Propriedade atualizar(UsuarioAutenticado usuario, UUID id, String nome, String municipio, JsonNode geometriaNode) {
        autorizacaoService.verificarDonoOuGestor(usuario, id);
        Propriedade propriedade = propriedadeRepository.findById(id)
                .orElseThrow(() -> new AppException(404, "Propriedade não encontrada."));
        MultiPolygon geometria = geometriaNode != null ? converterGeometria(geometriaNode) : propriedade.getGeometria();
        propriedade.atualizar(nome, municipio, geometria);
        return propriedadeRepository.save(propriedade);
    }

    @Transactional
    public void excluir(UsuarioAutenticado usuario, UUID id) {
        autorizacaoService.verificarDonoOuGestor(usuario, id);
        propriedadeRepository.deleteById(id);
    }

    /** 404 (nunca 403) para recurso fora do escopo visivel -- anti-enumeracao (RN018). */
    void verificarVisivelOu404(UsuarioAutenticado usuario, UUID id) {
        Optional<List<UUID>> visiveis = autorizacaoService.propriedadeIdsVisiveis(usuario);
        if (visiveis.isPresent() && !visiveis.get().contains(id)) {
            throw new AppException(404, "Propriedade não encontrada.");
        }
    }

    private MultiPolygon converterGeometria(JsonNode geometriaNode) {
        if (geometriaNode == null || geometriaNode.isNull()) {
            return null;
        }
        return GeometriaUtil.normalizarParaMultiPolygon(GeoJsonUtil.parseGeometria(geometriaNode.toString()));
    }
}
