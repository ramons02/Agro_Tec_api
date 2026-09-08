package com.agroclima.api.business.talhao;

import com.agroclima.api.core.base.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.MultiPolygon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "talhoes")
public class Talhao extends BaseModel<UUID> {

    @Id
    private UUID id;

    @Column(name = "propriedade_id", nullable = false)
    private UUID propriedadeId;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private MultiPolygon geometria;

    @Column(name = "area_ha", nullable = false, precision = 10, scale = 4)
    private BigDecimal areaHa;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo_solo", columnDefinition = "tipo_solo")
    private TipoSolo tipoSolo;

    @Column(name = "fracao_argila_pct", precision = 5, scale = 2)
    private BigDecimal fracaoArgilaPct;

    @Column(name = "fracao_areia_pct", precision = 5, scale = 2)
    private BigDecimal fracaoAreiaPct;

    @Column(name = "fracao_silte_pct", precision = 5, scale = 2)
    private BigDecimal fracaoSiltePct;

    @Column(name = "materia_organica_pct", precision = 5, scale = 2)
    private BigDecimal materiaOrganicaPct;

    @Column(name = "capacidade_agua_disponivel_mm", precision = 6, scale = 2)
    private BigDecimal capacidadeAguaDisponivelMm;

    private String cultura;

    @Column(name = "data_plantio")
    private LocalDate dataPlantio;

    protected Talhao() {}

    public Talhao(UUID propriedadeId, String nome, MultiPolygon geometria, double areaHa) {
        this.id = UUID.randomUUID();
        this.propriedadeId = propriedadeId;
        this.nome = nome;
        this.geometria = geometria;
        this.areaHa = escala(areaHa, 4);
    }

    public void parametrizarSolo(
            TipoSolo tipoSolo,
            double fracaoArgilaPct,
            double fracaoAreiaPct,
            double fracaoSiltePct,
            double materiaOrganicaPct,
            double capacidadeAguaDisponivelMm) {
        this.tipoSolo = tipoSolo;
        this.fracaoArgilaPct = escala(fracaoArgilaPct, 2);
        this.fracaoAreiaPct = escala(fracaoAreiaPct, 2);
        this.fracaoSiltePct = escala(fracaoSiltePct, 2);
        this.materiaOrganicaPct = escala(materiaOrganicaPct, 2);
        this.capacidadeAguaDisponivelMm = escala(capacidadeAguaDisponivelMm, 2);
    }

    private static BigDecimal escala(double valor, int casas) {
        return BigDecimal.valueOf(valor).setScale(casas, RoundingMode.HALF_UP);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getPropriedadeId() {
        return propriedadeId;
    }

    public String getNome() {
        return nome;
    }

    public MultiPolygon getGeometria() {
        return geometria;
    }

    public double getAreaHa() {
        return areaHa.doubleValue();
    }

    public TipoSolo getTipoSolo() {
        return tipoSolo;
    }

    public Double getFracaoArgilaPct() {
        return fracaoArgilaPct != null ? fracaoArgilaPct.doubleValue() : null;
    }

    public Double getFracaoAreiaPct() {
        return fracaoAreiaPct != null ? fracaoAreiaPct.doubleValue() : null;
    }

    public Double getFracaoSiltePct() {
        return fracaoSiltePct != null ? fracaoSiltePct.doubleValue() : null;
    }

    public Double getMateriaOrganicaPct() {
        return materiaOrganicaPct != null ? materiaOrganicaPct.doubleValue() : null;
    }

    public Double getCapacidadeAguaDisponivelMm() {
        return capacidadeAguaDisponivelMm != null ? capacidadeAguaDisponivelMm.doubleValue() : null;
    }

    public String getCultura() {
        return cultura;
    }

    public LocalDate getDataPlantio() {
        return dataPlantio;
    }
}
