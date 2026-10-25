package com.agroclima.api.business.alerta;

import com.agroclima.api.core.response.ApiEnvelope;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.security.UsuarioAutenticado;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Alertas do sino do app. Cada pessoa só vê e marca os próprios: não há como ler o de outra conta. */
@RestController
@RequestMapping("/api/v1/alertas")
public class AlertaController {

    public record AlertaResponse(UUID id, TipoAlerta tipo, String mensagem, UUID talhaoId, Instant criadoEm, boolean lido) {}

    public record ListaAlertas(long naoLidos, List<AlertaResponse> itens) {}

    private final AlertaRepository alertaRepository;

    public AlertaController(AlertaRepository alertaRepository) {
        this.alertaRepository = alertaRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiEnvelope<ListaAlertas> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        List<AlertaResponse> itens = alertaRepository.findTop50ByUsuarioIdOrderByCriadoEmDesc(usuario.id()).stream()
                .map(a -> new AlertaResponse(
                        a.getId(), a.getTipo(), a.getMensagem(), a.getTalhaoId(), a.getCriadoEm(), a.getLidoEm() != null))
                .toList();
        return ApiEnvelope.sucesso(new ListaAlertas(alertaRepository.countByUsuarioIdAndLidoEmIsNull(usuario.id()), itens));
    }

    @PostMapping("/{id}/lido")
    @Transactional
    public ResponseEntity<Void> marcarLido(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        Alerta alerta = alertaRepository.findByIdAndUsuarioId(id, usuario.id())
                .orElseThrow(() -> new AppException(404, "Alerta não encontrado."));
        alerta.marcarLido(Instant.now());
        alertaRepository.save(alerta);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lidos")
    @Transactional
    public ResponseEntity<Void> marcarTodosLidos(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        alertaRepository.marcarTodosLidos(usuario.id(), Instant.now());
        return ResponseEntity.noContent().build();
    }
}
