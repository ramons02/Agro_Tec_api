package com.agroclima.api.business.balancohidrico;

import com.agroclima.api.core.base.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** RD010/RN023 (Escopo V3) -- Kc por cultura e faixa de DAE, tabela publica da FAO. Nasce vazia. */
@Entity
@Table(name = "cultura_kc")
public class CulturaKc extends BaseModel<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String cultura;

    @Column(name = "fase_fenologica", nullable = false, length = 50)
    private String faseFenologica;

    @Column(name = "dae_inicio", nullable = false)
    private Integer daeInicio;

    @Column(name = "dae_fim", nullable = false)
    private Integer daeFim;

    @Column(name = "kc_valor", nullable = false, precision = 3, scale = 2)
    private BigDecimal kcValor;

    protected CulturaKc() {}

    @Override
    public Integer getId() {
        return id;
    }

    public String getCultura() {
        return cultura;
    }

    public String getFaseFenologica() {
        return faseFenologica;
    }

    public Integer getDaeInicio() {
        return daeInicio;
    }

    public Integer getDaeFim() {
        return daeFim;
    }

    public double getKcValor() {
        return kcValor.doubleValue();
    }
}
