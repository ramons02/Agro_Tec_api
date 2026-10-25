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
    private static final long INTERVALO_TESTE_SEGUNDOS = 30;

    public record Chaves(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {}

    public record AssinarRequest(@NotBlank @Size(max = 1000) String endpoint, @NotNull @Valid Chaves keys) {}

    public record RemoverRequest(@NotBlank @Size(max = 1000) String endpoint) {}

    private final AssinaturaPushRepository repositorio;
    private final ChavesVapid chaves;
    private final ValidadorDeEndpoint validador;
    private final NotificadorPush notificador;
    /** Último teste por conta: evita que um botão apertado sem parar vire spam no celular (e carga no serviço de push). */
    private final java.util.Map<java.util.UUID, Instant> ultimoTeste = new java.util.concurrent.ConcurrentHashMap<>();

    public PushController(
            AssinaturaPushRepository repositorio,
            ChavesVapid chaves,
            ValidadorDeEndpoint validador,
            NotificadorPush notificador) {
        this.notificador = notificador;
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

    /** Manda uma notificação de teste para os aparelhos da própria conta: confirma que os avisos chegam de verdade. */
    @PostMapping("/testar")
    public ApiEnvelope<Map<String, Integer>> testar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        Instant agora = Instant.now();
        Instant anterior = ultimoTeste.get(usuario.id());
        if (anterior != null && anterior.plusSeconds(INTERVALO_TESTE_SEGUNDOS).isAfter(agora)) {
            throw new AppException(429, "Espere alguns segundos para enviar outro teste.");
        }
        if (repositorio.countByUsuarioId(usuario.id()) == 0) {
            throw new AppException(422, "Nenhum aparelho com avisos ligados. Ligue os avisos no sino primeiro.");
        }
        ultimoTeste.put(usuario.id(), agora);
        int aceitos = notificador.enviar(
                usuario.id(), "AgroClima Pará", "Teste: os avisos no celular estão funcionando.", "/#/pulverizacao");
        return ApiEnvelope.sucesso(Map.of("enviados", aceitos));
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
