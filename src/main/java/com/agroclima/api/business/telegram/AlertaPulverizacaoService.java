package com.agroclima.api.business.telegram;

import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.propriedade.Propriedade;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.talhao.PulverizacaoService;
import com.agroclima.api.business.talhao.Talhao;
import com.agroclima.api.business.talhao.TalhaoRepository;
import com.agroclima.api.business.vinculo.EstadoVinculo;
import com.agroclima.api.business.vinculo.VinculoAgronomoPropriedadeRepository;
import com.agroclima.api.core.calculos.ClassificacaoPulverizacao;
import com.agroclima.api.core.calculos.PulverizacaoCalculos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Job periodico (feature 017, US2/US3): pra cada talhao com pelo menos um destinatario com
 * Telegram vinculado, classifica a pulverizacao atual (reaproveita PulverizacaoService, a
 * mesma logica do endpoint /pulverizacao) e dispara alerta na transicao bloqueada->FAVORAVEL
 * (US2) ou quando a rajada atual ultrapassa o limiar de vento forte (US3), independente de
 * transicao de estado. Falha de leitura climatica ou de envio nunca interrompe os demais
 * talhoes (FR-004, mesma degradacao graciosa do INMET/SoilGrids).
 */
@Service
public class AlertaPulverizacaoService {

    private static final Logger log = LoggerFactory.getLogger(AlertaPulverizacaoService.class);

    private final TalhaoRepository talhaoRepository;
    private final PropriedadeRepository propriedadeRepository;
    private final UsuarioRepository usuarioRepository;
    private final VinculoAgronomoPropriedadeRepository vinculoRepository;
    private final EstadoAlertaPulverizacaoRepository estadoRepository;
    private final PulverizacaoService pulverizacaoService;
    private final TelegramClient telegramClient;

    public AlertaPulverizacaoService(
            TalhaoRepository talhaoRepository,
            PropriedadeRepository propriedadeRepository,
            UsuarioRepository usuarioRepository,
            VinculoAgronomoPropriedadeRepository vinculoRepository,
            EstadoAlertaPulverizacaoRepository estadoRepository,
            PulverizacaoService pulverizacaoService,
            TelegramClient telegramClient) {
        this.talhaoRepository = talhaoRepository;
        this.propriedadeRepository = propriedadeRepository;
        this.usuarioRepository = usuarioRepository;
        this.vinculoRepository = vinculoRepository;
        this.estadoRepository = estadoRepository;
        this.pulverizacaoService = pulverizacaoService;
        this.telegramClient = telegramClient;
    }

    public record ResumoAlertas(int talhoesProcessados, int alertasEnviados) {}

    @Transactional
    public ResumoAlertas processarTodosTalhoes() {
        int processados = 0;
        int enviados = 0;
        for (Talhao talhao : talhaoRepository.findAll()) {
            try {
                List<String> chatIds = chatIdsComAcesso(talhao);
                if (chatIds.isEmpty()) {
                    continue; // ninguem pra avisar -- nem vale calcular o clima (FR-005/custo)
                }
                processados++;
                enviados += processarTalhao(talhao, chatIds);
            } catch (RuntimeException ex) {
                log.error("Falha ao processar alerta de pulverização do talhão {}: {}", talhao.getId(), ex.getMessage());
            }
        }
        return new ResumoAlertas(processados, enviados);
    }

    private List<String> chatIdsComAcesso(Talhao talhao) {
        Propriedade propriedade = propriedadeRepository.findById(talhao.getPropriedadeId()).orElse(null);
        if (propriedade == null) {
            return List.of();
        }
        Set<UUID> usuarioIds = new LinkedHashSet<>();
        usuarioIds.add(propriedade.getProprietarioId());
        vinculoRepository.findByPropriedadeIdAndEstado(propriedade.getId(), EstadoVinculo.ACEITO)
                .forEach(vinculo -> usuarioIds.add(vinculo.getAgronomoId()));

        return usuarioRepository.findAllById(usuarioIds).stream()
                .map(Usuario::getTelegramChatId)
                .filter(chatId -> chatId != null && !chatId.isBlank())
                .toList();
    }

    private int processarTalhao(Talhao talhao, List<String> chatIds) {
        Optional<PulverizacaoService.ResultadoPulverizacao> resultadoOpt = pulverizacaoService.classificarAtual(talhao);
        if (resultadoOpt.isEmpty()) {
            return 0;
        }
        PulverizacaoService.ResultadoPulverizacao resultado = resultadoOpt.get();
        int enviados = 0;

        if (classificacaoAbriuJanela(talhao.getId(), resultado.classificacaoFinal())) {
            String texto = "Talhão " + talhao.getNome() + ": janela de pulverização favorável agora.";
            enviados += enviarParaTodos(chatIds, texto);
        }

        Double rajada = resultado.rajadaKmh();
        if (rajada != null && rajada > PulverizacaoCalculos.LIMITE_RAJADA_MAX_FAVORAVEL) {
            String texto = "Talhão " + talhao.getNome() + ": rajada de " + Math.round(rajada)
                    + " km/h detectada, acima do limite seguro para pulverização.";
            enviados += enviarParaTodos(chatIds, texto);
        }

        return enviados;
    }

    /** Compara com o ultimo estado conhecido (US2/research.md) -- so "abriu" numa transicao
     * de bloqueada pra FAVORAVEL, nunca repete em ciclos consecutivos ja favoraveis. Sempre
     * atualiza o registro, mesmo sem alerta, pra manter o estado corrente. */
    private boolean classificacaoAbriuJanela(UUID talhaoId, ClassificacaoPulverizacao classificacaoAtual) {
        Instant agora = Instant.now();
        EstadoAlertaPulverizacao estado = estadoRepository.findById(talhaoId).orElse(null);
        ClassificacaoPulverizacao classificacaoAnterior =
                estado != null ? estado.getUltimaClassificacao() : null;

        if (estado == null) {
            estadoRepository.save(new EstadoAlertaPulverizacao(talhaoId, classificacaoAtual, agora));
        } else {
            estado.atualizar(classificacaoAtual, agora);
            estadoRepository.save(estado);
        }

        boolean estavaBloqueada = classificacaoAnterior != null && classificacaoAnterior != ClassificacaoPulverizacao.FAVORAVEL;
        boolean abriuAgora = classificacaoAtual == ClassificacaoPulverizacao.FAVORAVEL;
        return estavaBloqueada && abriuAgora;
    }

    private int enviarParaTodos(List<String> chatIds, String texto) {
        List<String> enviados = new ArrayList<>();
        for (String chatId : chatIds) {
            telegramClient.enviarMensagem(chatId, texto);
            enviados.add(chatId);
        }
        return enviados.size();
    }
}
