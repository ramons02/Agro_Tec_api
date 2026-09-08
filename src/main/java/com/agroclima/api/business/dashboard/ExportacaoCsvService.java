package com.agroclima.api.business.dashboard;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/** Espelha app/services/exportacao_csv_service.py (feature 015). Delimitador ";" e BOM UTF-8 (convencao Excel pt-BR). */
@Service
public class ExportacaoCsvService {

    private static final String BOM = "﻿";
    private static final String[] CABECALHO = {
        "Propriedade", "Talhao", "Area (ha)", "Solo", "Status", "Armazenamento (mm)", "% da CAD"
    };

    public String gerarCsvTalhoes(List<DashboardItemProjecao> itens) {
        StringBuilder csv = new StringBuilder(BOM);
        csv.append(String.join(";", CABECALHO)).append("\n");
        for (DashboardItemProjecao item : itens) {
            double areaHa = item.getAreaHa() != null ? item.getAreaHa() : 0.0;
            String solo = item.getTipoSolo() != null ? item.getTipoSolo() : "";
            String status = item.getStatusPlantio() != null ? item.getStatusPlantio() : "SEM_CALCULO";
            String armazenamento = item.getArmazenamentoMm() != null ? formatar1Casa(item.getArmazenamentoMm()) : "";
            String percentualCad = (item.getArmazenamentoMm() != null && item.getCadMm() != null && item.getCadMm() > 0)
                    ? formatar1Casa(item.getArmazenamentoMm() / item.getCadMm() * 100.0)
                    : "";
            csv.append(String.join(";",
                            item.getPropriedadeNome(),
                            item.getNome(),
                            formatar1Casa(areaHa),
                            solo,
                            status,
                            armazenamento,
                            percentualCad))
                    .append("\n");
        }
        return csv.toString();
    }

    private String formatar1Casa(double valor) {
        return String.format(Locale.US, "%.1f", valor);
    }
}
