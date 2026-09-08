package com.agroclima.api.business.dashboard;

import com.agroclima.api.core.security.AutorizacaoService;
import com.agroclima.api.core.security.UsuarioAutenticado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Espelha app/api/v1/endpoints/dashboard.py (features 011/015, RF026/RF027/RF034). */
@Service
public class DashboardService {

    private static final int LIMITE_EXPORTACAO = 10_000;

    private final DashboardRepository dashboardRepository;
    private final AutorizacaoService autorizacaoService;

    public DashboardService(DashboardRepository dashboardRepository, AutorizacaoService autorizacaoService) {
        this.dashboardRepository = dashboardRepository;
        this.autorizacaoService = autorizacaoService;
    }

    @Transactional(readOnly = true)
    public Page<DashboardItemProjecao> listar(
            UsuarioAutenticado usuario, UUID propriedadeIdFiltro, String status, int page, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), pageSize);
        return buscar(usuario, propriedadeIdFiltro, status, pageable);
    }

    @Transactional(readOnly = true)
    public List<DashboardItemProjecao> listarTudo(UsuarioAutenticado usuario, UUID propriedadeIdFiltro, String status) {
        Pageable semPaginacaoReal = PageRequest.of(0, LIMITE_EXPORTACAO);
        return buscar(usuario, propriedadeIdFiltro, status, semPaginacaoReal).getContent();
    }

    private Page<DashboardItemProjecao> buscar(
            UsuarioAutenticado usuario, UUID propriedadeIdFiltro, String status, Pageable pageable) {
        Optional<List<UUID>> visiveis = autorizacaoService.propriedadeIdsVisiveis(usuario);
        if (visiveis.isPresent()) {
            if (visiveis.get().isEmpty()) {
                // "IN ()" nao e valido -- lista vazia de propriedades visiveis so pode dar pagina vazia.
                return new PageImpl<>(List.of(), pageable, 0);
            }
            return dashboardRepository.buscarEscopado(visiveis.get(), propriedadeIdFiltro, status, pageable);
        }
        return dashboardRepository.buscarIrrestrito(propriedadeIdFiltro, status, pageable);
    }
}
