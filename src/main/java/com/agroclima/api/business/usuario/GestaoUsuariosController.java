package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.response.PagedResponse;
import com.agroclima.api.core.security.UsuarioAutenticado;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Monitoramento de contas (so gestor de tecnologia): quem esta criando conta e como usa o sistema. */
@RestController
@RequestMapping("/api/v1/usuarios")
public class GestaoUsuariosController {

    private static final int TAMANHO_PAGINA_PADRAO = 20;

    private final GestaoUsuariosService gestaoUsuariosService;

    public GestaoUsuariosController(GestaoUsuariosService gestaoUsuariosService) {
        this.gestaoUsuariosService = gestaoUsuariosService;
    }

    /** Mais recentes primeiro; filtro opcional por papel e busca por nome ou e-mail. */
    @GetMapping
    public ApiEnvelope<PagedResponse<GestaoUsuariosService.ItemUsuario>> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required = false) Papel papel,
            @RequestParam(name = "q", required = false) String busca,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "" + TAMANHO_PAGINA_PADRAO) int pageSize) {
        Page<GestaoUsuariosService.ItemUsuario> pagina =
                gestaoUsuariosService.listar(usuario, papel, busca, page, pageSize);
        return ApiEnvelope.sucesso(new PagedResponse<>(
                pagina.getContent(), pagina.getTotalElements(), page, pagina.getPageable().getPageSize()));
    }

    @GetMapping("/resumo")
    public ApiEnvelope<GestaoUsuariosService.Resumo> resumo(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ApiEnvelope.sucesso(gestaoUsuariosService.resumir(usuario));
    }
}
