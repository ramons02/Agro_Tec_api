package com.agroclima.api.business.vinculo;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.grupo.ParticipacaoAgronomoRepository;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.security.AutorizacaoService;
import com.agroclima.api.core.security.UsuarioAutenticado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Espelha app/api/v1/endpoints/vinculos.py (feature 014, convite de agronomo a propriedade). */
@Service
public class VinculoService {

    private final VinculoAgronomoPropriedadeRepository vinculoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutorizacaoService autorizacaoService;
    private final PropriedadeRepository propriedadeRepository;
    private final ParticipacaoAgronomoRepository participacaoRepository;

    public VinculoService(
            VinculoAgronomoPropriedadeRepository vinculoRepository,
            UsuarioRepository usuarioRepository,
            AutorizacaoService autorizacaoService,
            PropriedadeRepository propriedadeRepository,
            ParticipacaoAgronomoRepository participacaoRepository) {
        this.propriedadeRepository = propriedadeRepository;
        this.participacaoRepository = participacaoRepository;
        this.vinculoRepository = vinculoRepository;
        this.usuarioRepository = usuarioRepository;
        this.autorizacaoService = autorizacaoService;
    }

    @Transactional
    public VinculoAgronomoPropriedade convidar(UsuarioAutenticado usuario, UUID propriedadeId, String agronomoEmail) {
        autorizacaoService.verificarDonoOuGestor(usuario, propriedadeId);

        Usuario agronomo = usuarioRepository.findByEmail(agronomoEmail).orElse(null);
        if (agronomo == null || agronomo.getPapel() != Papel.AGRONOMO) {
            throw new AppException(422, "E-mail não corresponde a um usuário com papel AGRONOMO.");
        }

        // O vinculo so e permitido dentro do grupo da propriedade: o agronomo precisa pertencer a ele (FR-015).
        var grupoDaPropriedade = propriedadeRepository.findById(propriedadeId).map(p -> p.getGrupoId());
        boolean pertence = grupoDaPropriedade.isPresent()
                && participacaoRepository.findByIdAgronomoId(agronomo.getId()).stream()
                        .anyMatch(part -> part.getGrupoId().equals(grupoDaPropriedade.get()));
        if (!pertence) {
            throw new AppException(422, "O agrônomo não pertence ao grupo desta propriedade.");
        }

        return vinculoRepository.save(new VinculoAgronomoPropriedade(agronomo.getId(), propriedadeId));
    }

    @Transactional
    public VinculoAgronomoPropriedade aceitar(UsuarioAutenticado usuario, UUID vinculoId) {
        VinculoAgronomoPropriedade vinculo = vinculoRepository.findById(vinculoId)
                .orElseThrow(() -> new AppException(404, "Convite não encontrado."));

        if (!vinculo.getAgronomoId().equals(usuario.id())) {
            throw new AppException(403, "Este convite não é seu.");
        }

        vinculo.aceitar(Instant.now());
        return vinculoRepository.save(vinculo);
    }
}
