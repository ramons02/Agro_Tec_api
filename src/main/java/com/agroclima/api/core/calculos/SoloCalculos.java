package com.agroclima.api.core.calculos;

import com.agroclima.api.business.talhao.TipoSolo;

/** RD005 (textura) e RN020 (CAD), + pedotransferencia Saxton &amp; Rawls (1986). Porte literal de solo.py. */
public final class SoloCalculos {

    public static final double PROFUNDIDADE_RAIZES_PADRAO_MM = 200.0;
    public static final double LIMIAR_ARGILA_ARGILOSO_PCT = 35.0;
    public static final double LIMIAR_AREIA_ARENOSO_PCT = 70.0;
    public static final double LIMIAR_ARGILA_MAX_PARA_ARENOSO_PCT = 15.0;

    private SoloCalculos() {}

    /** siltePct nao entra na decisao -- presente so por completude de assinatura, igual ao Python. */
    public static TipoSolo classificarTextura(double argilaPct, double areiaPct, double siltePct) {
        if (argilaPct >= LIMIAR_ARGILA_ARGILOSO_PCT) {
            return TipoSolo.ARGILOSO;
        }
        if (areiaPct >= LIMIAR_AREIA_ARENOSO_PCT && argilaPct < LIMIAR_ARGILA_MAX_PARA_ARENOSO_PCT) {
            return TipoSolo.ARENOSO;
        }
        return TipoSolo.MISTO;
    }

    public static double calcularCad(
            double capacidadeCampoPct, double pontoMurchaPermanentePct, double densidadeSoloGCm3) {
        return calcularCad(capacidadeCampoPct, pontoMurchaPermanentePct, densidadeSoloGCm3, PROFUNDIDADE_RAIZES_PADRAO_MM);
    }

    public static double calcularCad(
            double capacidadeCampoPct, double pontoMurchaPermanentePct, double densidadeSoloGCm3, double profundidadeRaizesMm) {
        double ccFracao = capacidadeCampoPct / 100.0;
        double pmpFracao = pontoMurchaPermanentePct / 100.0;
        return (ccFracao - pmpFracao) * densidadeSoloGCm3 * profundidadeRaizesMm;
    }

    public record CcPmp(double ccPct, double pmpPct) {}

    public static CcPmp estimarCcPmp(double fracaoArgilaPct, double fracaoAreiaPct, double materiaOrganicaPct) {
        double areia = fracaoAreiaPct / 100.0;
        double argila = fracaoArgilaPct / 100.0;
        double om = materiaOrganicaPct;

        double pmpBruto = -0.024 * areia + 0.487 * argila + 0.006 * om
                + 0.005 * (areia * om) - 0.013 * (argila * om)
                + 0.068 * (areia * argila) + 0.031;
        double pmpFracao = pmpBruto + (0.14 * pmpBruto - 0.02);

        double ccBruto = -0.251 * areia + 0.195 * argila + 0.011 * om
                + 0.006 * (areia * om) - 0.027 * (argila * om)
                + 0.452 * (areia * argila) + 0.299;
        double ccFracao = ccBruto + (1.283 * ccBruto * ccBruto - 0.374 * ccBruto - 0.015);

        return new CcPmp(ccFracao * 100.0, Math.max(pmpFracao, 0.0) * 100.0);
    }
}
