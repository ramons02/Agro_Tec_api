package com.agroclima.api.business.push;

import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** Notificação push: o app pega a chave pública, o navegador cria a assinatura e o app a registra aqui. */
@RestController
@RequestMapping("/api/v1/push")
public class PushController {

    private static final int MAXIMO_POR_CONTA = 10;

    public record Chaves(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {}

    public record AssinarRequest(@NotBlank @Size(max = 1000) String endpoint, @NotNull @Valid Chaves keys) {}

    public record RemoverRequest(@NotBlank @Size(max = 1000) String endpoint) {}

    private final AssinaturaPushRepository repositorio;
    private final ChavesVapid chaves;
    private final ValidadorDeEndpoint validador;

    public PushController(AssinaturaPushRepository repositorio, ChavesVapid chaves, ValidadorDeEndpoint validador) {
        this.repositorio = repositorio;
        this.chaves = chaves;
        this.validador = validador;
    }

    @GetMapping("/chave")
    public ApiEnvelope<Map<String, String>> chavePublica() {
        return ApiEnvelope.sucesso(Map.of("chave_publica", chaves.obter().publica()));
    }

    @PostMapping("/assinaturas")
    @Transactional
    public ResponseEntity<Void> assinar(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @RequestBody AssinarRequest corpo) {
        if (!validador.valido(corpo.endpoint())) {
            throw new AppException(422, "Endereço de notificação não aceito.");
        }
        var existente = repositorio.findByEndpoint(corpo.endpoint());
        if (existente.isPresent()) {
            existente.get().reatribuir(usuario.id(), corpo.keys().p256dh(), corpo.keys().auth());
            repositorio.save(existente.get());
        } else {
            if (repositorio.countByUsuarioId(usuario.id()) >= MAXIMO_POR_CONTA) {
                throw new AppException(422, "Limite de aparelhos com notificação atingido.");
            }
            repositorio.save(new AssinaturaPush(
                    usuario.id(), corpo.endpoint(), corpo.keys().p256dh(), corpo.keys().auth(), Instant.now()));
        }
        return ResponseEntity.noContent().build();
    }

    /** POST (e não DELETE) porque o endpoint é longo demais para ir na URL e DELETE com corpo não é confiável. */
    @PostMapping("/assinaturas/remover")
    @Transactional
    public ResponseEntity<Void> remover(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @RequestBody RemoverRequest corpo) {
        repositorio.apagarDoUsuario(corpo.endpoint(), usuario.id());
        return ResponseEntity.noContent().build();
    }
}
