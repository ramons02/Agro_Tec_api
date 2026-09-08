package com.agroclima.api.business.balancohidrico;

import com.agroclima.api.core.base.BaseModel;
import com.agroclima.api.core.calculos.StatusPlantio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "balanco_hidrico_diario")
public class BalancoHidricoDiario extends BaseModel<UUID> {

    @Id
    private UUID id;

    @Column(name = "talhao_id", nullable = false)
    private UUID talhaoId;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "armazenamento_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal armazenamentoMm;

    @Column(name = "precipitacao_mm", nullable = false, precision = 5, scale = 2)
    private BigDecimal precipitacaoMm;

    @Column(name = "evapotranspiracao_mm", nullable = false, precision = 5, scale = 2)
    private BigDecimal evapotranspiracaoMm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_plantio", nullable = false, length = 20)
    private StatusPlantio statusPlantio;

    protected BalancoHidricoDiario() {}

    public BalancoHidricoDiario(
            UUID talhaoId,
            LocalDate data,
            double armazenamentoMm,
            double precipitacaoMm,
            double evapotranspiracaoMm,
            StatusPlantio statusPlantio) {
        this.id = UUID.randomUUID();
        this.talhaoId = talhaoId;
        this.data = data;
        this.armazenamentoMm = escala(armazenamentoMm, 2);
        this.precipitacaoMm = escala(precipitacaoMm, 2);
        this.evapotranspiracaoMm = escala(evapotranspiracaoMm, 2);
        this.statusPlantio = statusPlantio;
    }

    private static BigDecimal escala(double valor, int casas) {
        return BigDecimal.valueOf(valor).setScale(casas, RoundingMode.HALF_UP);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getTalhaoId() {
        return talhaoId;
    }

    public LocalDate getData() {
        return data;
    }

    public double getArmazenamentoMm() {
        return armazenamentoMm.doubleValue();
    }

    public double getPrecipitacaoMm() {
        return precipitacaoMm.doubleValue();
    }

    public double getEvapotranspiracaoMm() {
        return evapotranspiracaoMm.doubleValue();
    }

    public StatusPlantio getStatusPlantio() {
        return statusPlantio;
    }
}
