package com.agroclima.api.business.vinculo;

import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Espelha app/api/v1/endpoints/vinculos.py -- sem prefixo proprio, paths embutem o recurso pai. */
@RestController
@RequestMapping("/api/v1")
public class VinculoController {

    private final VinculoService vinculoService;

    public VinculoController(VinculoService vinculoService) {
        this.vinculoService = vinculoService;
    }

    public record VinculoRequest(@Email @NotBlank String agronomoEmail) {}

    @PostMapping("/propriedades/{propriedadeId}/vinculos")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> convidar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID propriedadeId,
            @Valid @RequestBody VinculoRequest requisicao) {
        VinculoAgronomoPropriedade vinculo = vinculoService.convidar(usuario, propriedadeId, requisicao.agronomoEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.sucesso(paraDados(vinculo)));
    }

    @PostMapping("/vinculos/{vinculoId}/aceitar")
    public ApiEnvelope<Map<String, Object>> aceitar(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID vinculoId) {
        VinculoAgronomoPropriedade vinculo = vinculoService.aceitar(usuario, vinculoId);
        return ApiEnvelope.sucesso(paraDados(vinculo));
    }

    private Map<String, Object> paraDados(VinculoAgronomoPropriedade vinculo) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("id", vinculo.getId().toString());
        dados.put("agronomo_id", vinculo.getAgronomoId().toString());
        dados.put("propriedade_id", vinculo.getPropriedadeId().toString());
        dados.put("estado", vinculo.getEstado());
        return dados;
    }
}
