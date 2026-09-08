package com.agroclima.api.core.calculos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PsicrometriaCalculosTest {

    @Test
    void bulboUmidoNuncaSuperaBulboSecoComUmidadeAbaixoDe100() {
        double tSeca = 30.0;
        double umido = PsicrometriaCalculos.calcularBulboUmido(tSeca, 60.0);
        assertThat(umido).isLessThan(tSeca);
    }

    @Test
    void umidadeRelativa100PctAproximaBulboUmidoDoSeco() {
        double tSeca = 25.0;
        double umido = PsicrometriaCalculos.calcularBulboUmido(tSeca, 100.0);
        // Na saturacao (UR=100%), bulbo umido converge pro bulbo seco.
        assertThat(umido).isCloseTo(tSeca, within(0.5));
    }

    @Test
    void deltaTEDiferencaEntreSecoEUmido() {
        double tSeca = 32.0;
        double umidade = 40.0;
        double esperado = tSeca - PsicrometriaCalculos.calcularBulboUmido(tSeca, umidade);
        assertThat(PsicrometriaCalculos.calcularDeltaT(tSeca, umidade)).isCloseTo(esperado, within(1e-9));
    }

    @Test
    void deltaTMaiorQuandoUmidadeMenor() {
        double deltaSeco = PsicrometriaCalculos.calcularDeltaT(30.0, 30.0);
        double deltaUmido = PsicrometriaCalculos.calcularDeltaT(30.0, 80.0);
        assertThat(deltaSeco).isGreaterThan(deltaUmido);
    }
}
