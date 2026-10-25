package com.agroclima.api.business.talhao;

import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.core.geo.GeometriaUtil;
import com.agroclima.api.core.geo.ValidacaoGeometria;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.security.AutorizacaoService;
import com.agroclima.api.core.security.UsuarioAutenticado;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
 * nao bloqueia) e o solo (SoilGrids) nunca bloqueia a criacao: e buscado em segundo plano
 * por {@link TalhaoSoloService}, porque o servico leva de 30 s a mais de 1 min para responder.
 */
@Service
public class TalhaoService {

    private static final Logger log = LoggerFactory.getLogger(TalhaoService.class);

    private final TalhaoRepository talhaoRepository;
    private final PropriedadeRepository propriedadeRepository;
    private final AutorizacaoService autorizacaoService;
    private final ImportacaoGeoService importacaoGeoService;
    private final ApplicationEventPublisher eventos;

    public TalhaoService(
            TalhaoRepository talhaoRepository,
            PropriedadeRepository propriedadeRepository,
            AutorizacaoService autorizacaoService,
            ImportacaoGeoService importacaoGeoService,
            ApplicationEventPublisher eventos) {
        this.talhaoRepository = talhaoRepository;
        this.propriedadeRepository = propriedadeRepository;
        this.autorizacaoService = autorizacaoService;
        this.importacaoGeoService = importacaoGeoService;
        this.eventos = eventos;
    }

    static final String MENSAGEM_FORA_DO_PARA =
            "O talhão precisa estar inteiramente dentro do estado do Pará. Este arquivo está fora do estado.";

    public record ResultadoCriacao(Talhao talhao, String aviso) {}

    @Transactional
    public ResultadoCriacao criarDeGeometria(
            UsuarioAutenticado usuario, UUID propriedadeId, String nome, Geometry geometriaBruta) {
        autorizacaoService.verificarDonoOuGestor(usuario, propriedadeId);
        propriedadeRepository.findById(propriedadeId)
                .orElseThrow(() -> new AppException(404, "Propriedade não encontrada."));

        if (!ValidacaoGeometria.geometriaValida(geometriaBruta)) {
            throw new AppException(422, "Geometria inválida.");
        }

        if (!ValidacaoGeometria.estaDentroDoPara(geometriaBruta)) {
            throw new AppException(422, MENSAGEM_FORA_DO_PARA,
                    Map.of("tipo", "FORA_DO_PARA", "requer_confirmacao", false));
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
                    + "' de outra propriedade, possível divisa em disputa.";
        }

        double areaHa = talhaoRepository.calcularAreaHa(geometria);
        Talhao talhao = new Talhao(propriedadeId, nome, geometria, areaHa);
        talhaoRepository.save(talhao);
        eventos.publishEvent(new TalhaoPrecisaDeSolo(talhao.getId()));

        return new ResultadoCriacao(talhao, aviso);
    }

    /**
     * Edita nome e geometria de um talhao existente (ex.: o shapefile foi corrigido). Aplica as mesmas
     * regras da criacao, ignorando o proprio talhao na checagem de sobreposicao, e recalcula area e solo.
     */
    @Transactional
    public ResultadoCriacao atualizarGeometria(
            UsuarioAutenticado usuario, UUID id, String nome, Geometry geometriaBruta) {
        Talhao talhao = talhaoRepository.findById(id).orElseThrow(() -> new AppException(404, "Talhão não encontrado."));
        autorizacaoService.verificarDonoOuGestor(usuario, talhao.getPropriedadeId());

        if (!ValidacaoGeometria.geometriaValida(geometriaBruta)) {
            throw new AppException(422, "Geometria inválida.");
        }
        if (!ValidacaoGeometria.estaDentroDoPara(geometriaBruta)) {
            throw new AppException(422, MENSAGEM_FORA_DO_PARA,
                    Map.of("tipo", "FORA_DO_PARA", "requer_confirmacao", false));
        }
        MultiPolygon geometria = GeometriaUtil.normalizarParaMultiPolygon(geometriaBruta);

        List<String> sobreposicaoMesma = talhaoRepository
                .buscarSobreposicaoMesmaPropriedadeExceto(talhao.getPropriedadeId(), id, geometria);
        if (!sobreposicaoMesma.isEmpty()) {
            throw new AppException(409,
                    "Geometria sobrepõe o talhão '" + sobreposicaoMesma.get(0) + "' na mesma propriedade.",
                    Map.of("tipo", "SOBREPOSICAO"));
        }

        String aviso = null;
        List<String> sobreposicaoOutra = talhaoRepository
                .buscarSobreposicaoOutraPropriedadeParaEdicao(talhao.getPropriedadeId(), geometria);
        if (!sobreposicaoOutra.isEmpty()) {
            aviso = "Geometria sobrepõe o talhão '" + sobreposicaoOutra.get(0)
                    + "' de outra propriedade, possível divisa em disputa.";
        }

        talhao.redefinirGeometria(nome, geometria, talhaoRepository.calcularAreaHa(geometria));
        talhao.limparSolo();
        talhaoRepository.save(talhao);
        eventos.publishEvent(new TalhaoPrecisaDeSolo(talhao.getId()));
        return new ResultadoCriacao(talhao, aviso);
    }

    @Transactional
    public ResultadoCriacao criarDeArquivo(
            UsuarioAutenticado usuario, UUID propriedadeId, String nome, String nomeArquivo, byte[] conteudo) {
        Geometry geometria = importacaoGeoService.extrairGeometria(nomeArquivo, conteudo);
        return criarDeGeometria(usuario, propriedadeId, nome, geometria);
    }

    /** Resultado da leitura de um arquivo sem cadastrar nada: a tela mostra o poligono no mapa e decide. */
    public record GeometriaLida(Geometry geometria, boolean dentroDoPara, String mensagem) {}

    /**
     * Le GeoJSON, KML ou .zip de Shapefile e devolve a geometria para a tela desenhar. Um arquivo fora do
     * Para volta com {@code dentroDoPara=false} para a pessoa ver onde ele cai; o cadastro continua recusando.
     */
    @Transactional(readOnly = true)
    public GeometriaLida lerGeometriaDeArquivo(String nomeArquivo, byte[] conteudo) {
        Geometry geometria = importacaoGeoService.extrairGeometria(nomeArquivo, conteudo);
        if (!ValidacaoGeometria.geometriaValida(geometria)) {
            throw new AppException(422, "Geometria inválida.");
        }
        boolean dentro = ValidacaoGeometria.estaDentroDoPara(geometria);
        return new GeometriaLida(geometria, dentro, dentro ? null : MENSAGEM_FORA_DO_PARA);
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
