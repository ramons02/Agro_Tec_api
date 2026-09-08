package com.agroclima.api.core.calculos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class IdwCalculosTest {

    @Test
    void listaVaziaLancaExcecao() {
        assertThatThrownBy(() -> IdwCalculos.interpolar(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void distanciaZeroRetornaValorDaEstacaoDireto() {
        double resultado = IdwCalculos.interpolar(List.of(
                new IdwCalculos.ValorDistancia(10.0, 5.0),
                new IdwCalculos.ValorDistancia(99.0, 0.0),
                new IdwCalculos.ValorDistancia(20.0, 2.0)));
        assertThat(resultado).isEqualTo(99.0);
    }

    @Test
    void interpolaPorInversoDoQuadradoDaDistancia() {
        // valor1=10 dist=1 (peso 1) ; valor2=20 dist=2 (peso 0.25)
        // numerador = 10*1 + 20*0.25 = 15 ; denominador = 1 + 0.25 = 1.25 -> 12.0
        double resultado = IdwCalculos.interpolar(List.of(
                new IdwCalculos.ValorDistancia(10.0, 1.0),
                new IdwCalculos.ValorDistancia(20.0, 2.0)));
        assertThat(resultado).isCloseTo(12.0, within(1e-9));
    }

    @Test
    void umaUnicaEstacaoRetornaOProprioValor() {
        double resultado = IdwCalculos.interpolar(List.of(new IdwCalculos.ValorDistancia(7.5, 3.0)));
        assertThat(resultado).isEqualTo(7.5);
    }
}
