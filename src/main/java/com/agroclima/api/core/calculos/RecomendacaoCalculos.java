package com.agroclima.api.core.calculos;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * RN011-RN013/RN019, FR-001-FR-008 -- sintese de status de plantio + pulverizacao numa
 * recomendacao unica de "proximo passo". Porte literal de recomendacao.py, incluindo os
 * textos em portugues (usados em teste, nao reescrever).
 */
public final class RecomendacaoCalculos {

    public static final double LIMITE_TENDENCIA_PP = 1.5;
    public static final String AVISO_FIXO =
            "Sugestão gerada automaticamente — não substitui avaliação agronômica profissional.";

    private static final Map<TendenciaUmidade, String> TEXTO_AMARELO_POR_TENDENCIA = new EnumMap<>(TendenciaUmidade.class);
    private static final Map<ClassificacaoPulverizacao, String> CLAUSULA_PULVERIZACAO =
            new EnumMap<>(ClassificacaoPulverizacao.class);
    private static final Set<ClassificacaoPulverizacao> BLOQUEIOS_PULVERIZACAO = EnumSet.of(
            ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE,
            ClassificacaoPulverizacao.BLOQUEIO_INVERSAO_TERMICA,
            ClassificacaoPulverizacao.BLOQUEIO_EVAPORACAO_EXCESSIVA);

    static {
        TEXTO_AMARELO_POR_TENDENCIA.put(TendenciaUmidade.SUBINDO,
                "Solo em nível de atenção e melhorando — monitore antes de decidir o plantio.");
        TEXTO_AMARELO_POR_TENDENCIA.put(TendenciaUmidade.CAINDO,
                "Solo em nível de atenção e piorando — a janela de plantio pode fechar em breve.");
        TEXTO_AMARELO_POR_TENDENCIA.put(TendenciaUmidade.ESTAVEL,
                "Solo em nível de atenção estável — vale reavaliar antes de decidir o plantio.");

        CLAUSULA_PULVERIZACAO.put(ClassificacaoPulverizacao.FAVORAVEL,
                "Janela de pulverização liberada agora.");
        CLAUSULA_PULVERIZACAO.put(ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE,
                "Pulverização bloqueada por vento forte — aguarde a próxima checagem.");
        CLAUSULA_PULVERIZACAO.put(ClassificacaoPulverizacao.BLOQUEIO_INVERSAO_TERMICA,
                "Pulverização bloqueada por inversão térmica — não aplique defensivos até a condição normalizar.");
        CLAUSULA_PULVERIZACAO.put(ClassificacaoPulverizacao.BLOQUEIO_EVAPORACAO_EXCESSIVA,
                "Pulverização bloqueada por evaporação excessiva — a calda pode não atingir o alvo.");
    }

    private RecomendacaoCalculos() {}

    public record Recomendacao(String texto, Prioridade prioridade, String aviso) {
        public Recomendacao(String texto, Prioridade prioridade) {
            this(texto, prioridade, AVISO_FIXO);
        }
    }

    public static TendenciaUmidade calcularTendenciaUmidade(
            double armazenamentoHojeMm, double armazenamento3DiasAtrasMm, double cadMm) {
        if (cadMm <= 0) {
            return TendenciaUmidade.ESTAVEL;
        }
        double diferencaPp = (armazenamentoHojeMm - armazenamento3DiasAtrasMm) / cadMm * 100;
        if (diferencaPp >= LIMITE_TENDENCIA_PP) {
            return TendenciaUmidade.SUBINDO;
        }
        if (diferencaPp <= -LIMITE_TENDENCIA_PP) {
            return TendenciaUmidade.CAINDO;
        }
        return TendenciaUmidade.ESTAVEL;
    }

    private static String textoPlantio(StatusPlantio statusPlantio, TendenciaUmidade tendenciaUmidade) {
        if (statusPlantio == null) {
            return "Ainda sem balanço hídrico calculado para este talhão.";
        }
        return switch (statusPlantio) {
            case VERMELHO -> "Solo em risco crítico — evite tráfego de maquinário pesado até a umidade se recuperar.";
            case AMARELO -> TEXTO_AMARELO_POR_TENDENCIA.get(tendenciaUmidade);
            case VERDE -> "Solo em condição ideal para plantio — sem restrições hídricas no momento.";
        };
    }

    public static Recomendacao gerarRecomendacao(
            StatusPlantio statusPlantio, ClassificacaoPulverizacao statusPulverizacao, TendenciaUmidade tendenciaUmidade) {
        String texto = textoPlantio(statusPlantio, tendenciaUmidade);
        boolean pulverizacaoBloqueada = statusPulverizacao != null && BLOQUEIOS_PULVERIZACAO.contains(statusPulverizacao);

        texto += " " + (statusPulverizacao != null
                ? CLAUSULA_PULVERIZACAO.get(statusPulverizacao)
                : "Sem dado de pulverização disponível no momento.");

        Prioridade prioridade;
        if (statusPlantio == StatusPlantio.VERMELHO) {
            prioridade = Prioridade.ALTA;
        } else if (statusPlantio == StatusPlantio.AMARELO || pulverizacaoBloqueada) {
            prioridade = Prioridade.MEDIA;
        } else {
            prioridade = Prioridade.BAIXA;
        }

        return new Recomendacao(texto, prioridade);
    }

    public static Recomendacao gerarRecomendacao(StatusPlantio statusPlantio, ClassificacaoPulverizacao statusPulverizacao) {
        return gerarRecomendacao(statusPlantio, statusPulverizacao, TendenciaUmidade.ESTAVEL);
    }
}
