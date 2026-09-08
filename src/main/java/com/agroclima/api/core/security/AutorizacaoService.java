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
 * RN018/FR-006 -- RBAC de propriedade/talhao. Nao da pra usar so @PreAuthorize porque o
 * escopo e a nivel de dado (linha), nao so de endpoint -- espelha verificar_dono_ou_gestor
 * e propriedade_ids_visiveis de app/core/security.py.
 */
@Service
public class AutorizacaoService {

    private static final String ERRO_AGRONOMO_SOMENTE_LEITURA = "Agrônomos têm acesso somente leitura.";
    private static final String ERRO_SEM_PERMISSAO_ESCRITA = "Você não tem permissão para alterar esta propriedade.";

    private final PropriedadeRepository propriedadeRepository;
    private final VinculoAgronomoPropriedadeRepository vinculoRepository;

    public AutorizacaoService(
            PropriedadeRepository propriedadeRepository, VinculoAgronomoPropriedadeRepository vinculoRepository) {
        this.propriedadeRepository = propriedadeRepository;
        this.vinculoRepository = vinculoRepository;
    }

    /**
     * 403 sempre explicito (nunca um 404 disfarcado), mesmo se a propriedade nao existir --
     * evita vazar existencia via um erro diferente.
     */
    public void verificarDonoOuGestor(UsuarioAutenticado usuario, UUID propriedadeId) {
        if (usuario.papel() == Papel.GESTOR_TECNOLOGIA) {
            return;
        }
        if (usuario.papel() == Papel.AGRONOMO) {
            throw new AppException(403, ERRO_AGRONOMO_SOMENTE_LEITURA, java.util.Map.of("papel", usuario.papel()));
        }
        Propriedade propriedade = propriedadeRepository.findById(propriedadeId).orElse(null);
        if (propriedade == null || !propriedade.getProprietarioId().equals(usuario.id())) {
            throw new AppException(403, ERRO_SEM_PERMISSAO_ESCRITA, java.util.Map.of("papel", usuario.papel()));
        }
    }

    /** Optional.empty() = sem restricao (ve tudo, so GESTOR_TECNOLOGIA); presente = lista de ids visiveis. */
    public Optional<List<UUID>> propriedadeIdsVisiveis(UsuarioAutenticado usuario) {
        if (usuario.papel() == Papel.GESTOR_TECNOLOGIA) {
            return Optional.empty();
        }
        if (usuario.papel() == Papel.AGRONOMO) {
            List<UUID> ids = vinculoRepository.findByAgronomoIdAndEstado(usuario.id(), EstadoVinculo.ACEITO).stream()
                    .map(vinculo -> vinculo.getPropriedadeId())
                    .toList();
            return Optional.of(ids);
        }
        // PRODUTOR_RURAL (ou qualquer outro papel): so as proprias propriedades.
        return Optional.of(propriedadeRepository.findIdByProprietarioId(usuario.id()));
    }
}
