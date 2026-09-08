package com.agroclima.api.business.talhao;

import com.agroclima.api.business.clima.clients.FonteSoloIndisponivelException;
import com.agroclima.api.business.clima.clients.PerfilSolo;
import com.agroclima.api.business.clima.clients.SoilGridsClient;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.core.calculos.SoloCalculos;
import com.agroclima.api.core.geo.GeometriaUtil;
import com.agroclima.api.core.geo.ValidacaoGeometria;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.security.AutorizacaoService;
import com.agroclima.api.core.security.UsuarioAutenticado;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Espelha _criar_talhao_a_partir_de_poligono() de app/api/v1/endpoints/talhoes.py -- a
 * logica de porte mais arriscada: 3 semanticas de erro distintas (422 geometria/fora do
 * Para, 409 sobreposicao mesma propriedade bloqueia, aviso sobreposicao outra propriedade
 * nao bloqueia) e SoilGrids nunca bloqueia a criacao se falhar.
 */
@Service
public class TalhaoService {

    private static final Logger log = LoggerFactory.getLogger(TalhaoService.class);

    private final TalhaoRepository talhaoRepository;
    private final PropriedadeRepository propriedadeRepository;
    private final AutorizacaoService autorizacaoService;
    private final ImportacaoGeoService importacaoGeoService;
    private final SoilGridsClient soilGridsClient;

    public TalhaoService(
            TalhaoRepository talhaoRepository,
            PropriedadeRepository propriedadeRepository,
            AutorizacaoService autorizacaoService,
            ImportacaoGeoService importacaoGeoService,
            SoilGridsClient soilGridsClient) {
        this.talhaoRepository = talhaoRepository;
        this.propriedadeRepository = propriedadeRepository;
        this.autorizacaoService = autorizacaoService;
        this.importacaoGeoService = importacaoGeoService;
        this.soilGridsClient = soilGridsClient;
    }

    public record ResultadoCriacao(Talhao talhao, String aviso) {}

    @Transactional
    public ResultadoCriacao criarDeGeometria(
            UsuarioAutenticado usuario, UUID propriedadeId, String nome, Geometry geometriaBruta, boolean confirmarForaDoPara) {
        autorizacaoService.verificarDonoOuGestor(usuario, propriedadeId);
        propriedadeRepository.findById(propriedadeId)
                .orElseThrow(() -> new AppException(404, "Propriedade não encontrada."));

        if (!ValidacaoGeometria.geometriaValida(geometriaBruta)) {
            throw new AppException(422, "Geometria inválida.");
        }

        Point centroide = geometriaBruta.getCentroid();
        if (!ValidacaoGeometria.estaDentroDoPara(centroide) && !confirmarForaDoPara) {
            throw new AppException(422, "Talhão fora da área esperada do Pará. Confirme para prosseguir.",
                    Map.of("tipo", "FORA_DO_PARA", "requer_confirmacao", true));
        }

        MultiPolygon geometria = GeometriaUtil.normalizarParaMultiPolygon(geometriaBruta);

        List<String> sobreposicaoMesma = talhaoRepository.buscarSobreposicaoMesmaPropriedade(propriedadeId, geometria);
        if (!sobreposicaoMesma.isEmpty()) {
            throw new AppException(409,
                    "Geometria sobrepõe o talhão '" + sobreposicaoMesma.get(0) + "' na mesma propriedade.",
                    Map.of("tipo", "SOBREPOSICAO"));
        }

        String aviso = null;
        List<String> sobreposicaoOutra = talhaoRepository.buscarSobreposicaoOutraPropriedade(propriedadeId, geometria);
        if (!sobreposicaoOutra.isEmpty()) {
            aviso = "Geometria sobrepõe o talhão '" + sobreposicaoOutra.get(0)
                    + "' de outra propriedade — possível divisa em disputa.";
        }

        double areaHa = talhaoRepository.calcularAreaHa(geometria);
        Talhao talhao = new Talhao(propriedadeId, nome, geometria, areaHa);
        parametrizarSoloSemBloquear(talhao, centroide);
        talhaoRepository.save(talhao);

        return new ResultadoCriacao(talhao, aviso);
    }

    @Transactional
    public ResultadoCriacao criarDeArquivo(
            UsuarioAutenticado usuario, UUID propriedadeId, String nome, String nomeArquivo, byte[] conteudo,
            boolean confirmarForaDoPara) {
        Geometry geometria = importacaoGeoService.extrairGeometria(nomeArquivo, conteudo);
        return criarDeGeometria(usuario, propriedadeId, nome, geometria, confirmarForaDoPara);
    }

    /** SoilGrids nunca bloqueia a criacao do talhao -- nem em erro, nem em "sem cobertura" (RN017). */
    private void parametrizarSoloSemBloquear(Talhao talhao, Point centroide) {
        try {
            Optional<PerfilSolo> perfilOpcional = soilGridsClient.parametrizarSolo(centroide.getY(), centroide.getX());
            if (perfilOpcional.isEmpty()) {
                log.warn("SoilGrids sem cobertura para o talhão em ({}, {})", centroide.getY(), centroide.getX());
                return;
            }
            PerfilSolo perfil = perfilOpcional.get();
            var tipoSolo = SoloCalculos.classificarTextura(
                    perfil.fracaoArgilaPct(), perfil.fracaoAreiaPct(), perfil.fracaoSiltePct());
            var ccPmp = SoloCalculos.estimarCcPmp(
                    perfil.fracaoArgilaPct(), perfil.fracaoAreiaPct(), perfil.materiaOrganicaPct());
            double cad = SoloCalculos.calcularCad(ccPmp.ccPct(), ccPmp.pmpPct(), perfil.densidadeSoloGCm3());
            talhao.parametrizarSolo(tipoSolo, perfil.fracaoArgilaPct(), perfil.fracaoAreiaPct(),
                    perfil.fracaoSiltePct(), perfil.materiaOrganicaPct(), cad);
        } catch (FonteSoloIndisponivelException ex) {
            log.warn("SoilGrids indisponível ao criar talhão: {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<Talhao> listar(UsuarioAutenticado usuario, UUID propriedadeId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), pageSize);
        if (propriedadeId != null) {
            autorizacaoService.propriedadeIdsVisiveis(usuario).ifPresent(ids -> {
                if (!ids.contains(propriedadeId)) {
                    throw new AppException(404, "Propriedade não encontrada.");
                }
            });
            return talhaoRepository.findByPropriedadeId(propriedadeId, pageable);
        }
        Optional<List<UUID>> visiveis = autorizacaoService.propriedadeIdsVisiveis(usuario);
        return visiveis
                .map(ids -> talhaoRepository.findByPropriedadeIdIn(ids, pageable))
                .orElseGet(() -> talhaoRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    public Talhao buscarVisivelOu404(UsuarioAutenticado usuario, UUID id) {
        Talhao talhao = talhaoRepository.findById(id).orElseThrow(() -> new AppException(404, "Talhão não encontrado."));
        Optional<List<UUID>> visiveis = autorizacaoService.propriedadeIdsVisiveis(usuario);
        if (visiveis.isPresent() && !visiveis.get().contains(talhao.getPropriedadeId())) {
            throw new AppException(404, "Talhão não encontrado.");
        }
        return talhao;
    }

    @Transactional
    public void excluir(UsuarioAutenticado usuario, UUID id) {
        Talhao talhao = talhaoRepository.findById(id).orElseThrow(() -> new AppException(404, "Talhão não encontrado."));
        autorizacaoService.verificarDonoOuGestor(usuario, talhao.getPropriedadeId());
        talhaoRepository.deleteById(id);
    }
}
