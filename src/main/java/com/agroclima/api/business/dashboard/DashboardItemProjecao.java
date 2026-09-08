package com.agroclima.api.business.dashboard;

import java.util.UUID;

public interface DashboardItemProjecao {

    UUID getTalhaoId();

    String getNome();

    String getPropriedadeNome();

    Double getAreaHa();

    String getTipoSolo();

    String getStatusPlantio();

    Double getArmazenamentoMm();

    Double getCadMm();
}
