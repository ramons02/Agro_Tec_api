package com.agroclima.api.core.security;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.vinculo.EstadoVinculo;
import com.agroclima.api.business.vinculo.VinculoAgronomoPropriedadeRepository;
import com.agroclima.api.core.response.AppException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * RN018/FR-006 -- RBAC de propriedade/talhao, com o escopo de grupo da spec 024. Nao da pra usar so
 * @PreAuthorize porque o escopo e a nivel de dado (linha), nao so de endpoint. A regra, de cima para baixo:
 * o Admin Master ve tudo; o Admin ve os dados do grupo dele; o produtor, as proprias propriedades; o agronomo,
 * as propriedades a que esta vinculado (convite aceito) e que estao num grupo dele. O papel e o grupo vem do
 * banco (EstadoContaCache), nao do token.
 */
@Service
public class AutorizacaoService {

    private static final String ERRO_AGRONOMO_SOMENTE_LEITURA = "Agrônomos têm acesso somente leitura.";
    private static final String ERRO_SEM_PERMISSAO_ESCRITA = "Você não tem permissão para alterar esta propriedade.";

    private final PropriedadeRepository propriedadeRepository;
    private final VinculoAgronomoPropriedadeRepository vinculoRepository;
    private final EstadoContaCache estadoContaCache;

    public AutorizacaoService(
            PropriedadeRepository propriedadeRepository,
            VinculoAgronomoPropriedadeRepository vinculoRepository,
            EstadoContaCache estadoContaCache) {
        this.propriedadeRepository = propriedadeRepository;
        this.vinculoRepository = vinculoRepository;
        this.estadoContaCache = estadoContaCache;
    }

    /** Admin Master: o unico papel sem filtro de grupo. */
    public boolean ehMaster(UsuarioAutenticado usuario) {
        return usuario.papel() == Papel.ADMIN_MASTER;
    }

    /** Grupo do Admin ou do produtor; vazio para o agronomo e para conta ainda sem grupo. */
    public Optional<UUID> grupoDe(UsuarioAutenticado usuario) {
        return estadoContaCache.obter(usuario.id()).map(EstadoConta::grupoId);
    }

    /** Grupos do agronomo (varios); vazio para os demais papeis. */
    public List<UUID> gruposDoAgronomo(UsuarioAutenticado usuario) {
        return estadoContaCache.obter(usuario.id()).map(EstadoConta::gruposDoAgronomo).orElse(List.of());
    }

    /**
     * 403 sempre explicito (nunca um 404 disfarcado), mesmo se a propriedade nao existir --
     * evita vazar existencia via um erro diferente.
     */
    public void verificarDonoOuGestor(UsuarioAutenticado usuario, UUID propriedadeId) {
        if (ehMaster(usuario)) {
            return;
        }
        if (usuario.papel() == Papel.AGRONOMO) {
            throw new AppException(403, ERRO_AGRONOMO_SOMENTE_LEITURA, java.util.Map.of("papel", usuario.papel()));
        }
        Propriedade propriedade = propriedadeRepository.findById(propriedadeId).orElse(null);
        boolean permitido = propriedade != null
                && (usuario.papel() == Papel.GESTOR_TECNOLOGIA
                        ? grupoDe(usuario).map(g -> g.equals(propriedade.getGrupoId())).orElse(false)
                        : propriedade.getProprietarioId().equals(usuario.id()));
        if (!permitido) {
            throw new AppException(403, ERRO_SEM_PERMISSAO_ESCRITA, java.util.Map.of("papel", usuario.papel()));
        }
    }

    /** Telas e dados de administracao (contas) so para Admin e Master; 403 explicito para os demais. */
    public void exigirGestor(UsuarioAutenticado usuario) {
        if (!usuario.papel().ehGestor()) {
            throw new AppException(403, "Somente gestor de tecnologia pode ver os usuários.");
        }
    }

    /** Optional.empty() = sem restricao (so o Master); presente = lista de ids visiveis. */
    public Optional<List<UUID>> propriedadeIdsVisiveis(UsuarioAutenticado usuario) {
        if (ehMaster(usuario)) {
            return Optional.empty();
        }
        if (usuario.papel() == Papel.GESTOR_TECNOLOGIA) {
            // Admin sem grupo (conta legada ou ainda nao atribuida) nao ve nada: nunca "tudo".
            return Optional.of(grupoDe(usuario).map(propriedadeRepository::findIdByGrupoId).orElse(List.of()));
        }
        if (usuario.papel() == Papel.AGRONOMO) {
            List<UUID> vinculadas = vinculoRepository.findByAgronomoIdAndEstado(usuario.id(), EstadoVinculo.ACEITO).stream()
                    .map(vinculo -> vinculo.getPropriedadeId())
                    .toList();
            List<UUID> grupos = gruposDoAgronomo(usuario);
            if (vinculadas.isEmpty() || grupos.isEmpty()) {
                return Optional.of(List.of());
            }
            // O vinculo so vale enquanto a propriedade esta num grupo do agronomo (spec 024, FR-015).
            return Optional.of(propriedadeRepository.findIdByIdInAndGrupoIdIn(vinculadas, grupos));
        }
        // PRODUTOR_RURAL (ou qualquer outro papel): so as proprias propriedades.
        return Optional.of(propriedadeRepository.findIdByProprietarioId(usuario.id()));
    }
}
