package com.agroclima.api.business.estacao;

import com.agroclima.api.core.base.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "medicoes_clima")
public class MedicaoClima extends BaseModel<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "estacao_codigo", nullable = false, length = 10)
    private String estacaoCodigo;

    @Column(name = "data_hora_utc", nullable = false)
    private Instant dataHoraUtc;

    @Column(name = "precipitacao_mm", precision = 5, scale = 2)
    private BigDecimal precipitacaoMm;

    @Column(name = "temperatura_c", precision = 4, scale = 2)
    private BigDecimal temperaturaC;

    @Column(name = "umidade_pct", precision = 4, scale = 2)
    private BigDecimal umidadePct;

    @Column(name = "vento_velocidade_ms", precision = 4, scale = 2)
    private BigDecimal ventoVelocidadeMs;

    @Column(name = "vento_rajada_ms", precision = 4, scale = 2)
    private BigDecimal ventoRajadaMs;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "fonte_dados", nullable = false, columnDefinition = "fonte_dados_medicao")
    private FonteDados fonteDados;

    protected MedicaoClima() {}

    public MedicaoClima(
            String estacaoCodigo,
            Instant dataHoraUtc,
            Double precipitacaoMm,
            Double temperaturaC,
            Double umidadePct,
            Double ventoVelocidadeMs,
            Double ventoRajadaMs,
            FonteDados fonteDados) {
        this.estacaoCodigo = estacaoCodigo;
        this.dataHoraUtc = dataHoraUtc;
        this.precipitacaoMm = escala(precipitacaoMm, 2);
        this.temperaturaC = escala(temperaturaC, 2);
        this.umidadePct = escala(umidadePct, 2);
        this.ventoVelocidadeMs = escala(ventoVelocidadeMs, 2);
        this.ventoRajadaMs = escala(ventoRajadaMs, 2);
        this.fonteDados = fonteDados;
    }

    private static BigDecimal escala(Double valor, int casas) {
        return valor == null ? null : BigDecimal.valueOf(valor).setScale(casas, RoundingMode.HALF_UP);
    }

    @Override
    public Long getId() {
        return id;
    }

    public String getEstacaoCodigo() {
        return estacaoCodigo;
    }

    public Instant getDataHoraUtc() {
        return dataHoraUtc;
    }

    public Double getPrecipitacaoMm() {
        return precipitacaoMm != null ? precipitacaoMm.doubleValue() : null;
    }

    public Double getTemperaturaC() {
        return temperaturaC != null ? temperaturaC.doubleValue() : null;
    }

    public Double getUmidadePct() {
        return umidadePct != null ? umidadePct.doubleValue() : null;
    }

    public Double getVentoVelocidadeMs() {
        return ventoVelocidadeMs != null ? ventoVelocidadeMs.doubleValue() : null;
    }

    public Double getVentoRajadaMs() {
        return ventoRajadaMs != null ? ventoRajadaMs.doubleValue() : null;
    }

    public FonteDados getFonteDados() {
        return fonteDados;
    }
}
