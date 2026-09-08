package com.agroclima.api.core.calculos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BalancoHidricoCalculosTest {

    @Test
    void calculaArmazenamentoComKcPadrao() {
        // ARM_i-1=40, P=10, ET0=12.5, Kc=0.4 -> ET_real=5.0 -> 40+10-5=45
        double resultado = BalancoHidricoCalculos.calcularArmazenamento(40.0, 10.0, 12.5, 100.0);
        assertThat(resultado).isCloseTo(45.0, within(1e-9));
    }

    @Test
    void clampaNoTetoDaCad() {
        double resultado = BalancoHidricoCalculos.calcularArmazenamento(90.0, 50.0, 0.0, 100.0);
        assertThat(resultado).isEqualTo(100.0);
    }

    @Test
    void clampaNoPisoZero() {
        double resultado = BalancoHidricoCalculos.calcularArmazenamento(5.0, 0.0, 50.0, 100.0, 1.0);
        assertThat(resultado).isEqualTo(0.0);
    }

    @Test
    void armazenamentoInicialE70PorCentoDaCad() {
        assertThat(BalancoHidricoCalculos.armazenamentoInicial(100.0)).isCloseTo(70.0, within(1e-9));
    }
}
