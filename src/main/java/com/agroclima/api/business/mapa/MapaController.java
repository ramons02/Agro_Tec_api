package com.agroclima.api.business.mapa;

import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.security.UsuarioAutenticado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Espelha app/api/v1/endpoints/mapa.py. */
@RestController
@RequestMapping("/api/v1/mapa")
public class MapaController {

    private final MapaService mapaService;

    public MapaController(MapaService mapaService) {
        this.mapaService = mapaService;
    }

    @GetMapping("/dados")
    public ApiEnvelope<MapaService.DadosMapa> dados(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ApiEnvelope.sucesso(mapaService.obterDadosMapa(usuario));
    }
}
