package com.agroclima.api.core.calculos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusPlantioCalculosTest {

    private static final double CAD = 100.0;

    @Test
    void exatamente30PctEAmareloNaoVermelho() {
        // limite e "< 30%", 30% exato fica do lado de dentro (nao bate a condicao de VERMELHO).
        assertThat(StatusPlantioCalculos.classificarStatus(30.0, CAD, 0.0)).isEqualTo(StatusPlantio.AMARELO);
    }

    @Test
    void abaixoDe30PctEVermelhoPorSeca() {
        assertThat(StatusPlantioCalculos.classificarStatus(29.9, CAD, 10.0)).isEqualTo(StatusPlantio.VERMELHO);
    }

    @Test
    void exatamente95PctEAmareloNaoVermelho() {
        // limite e "> 95%", 95% exato fica do lado de dentro.
        assertThat(StatusPlantioCalculos.classificarStatus(95.0, CAD, 0.0)).isEqualTo(StatusPlantio.AMARELO);
    }

    @Test
    void acimaDe95PctEVermelhoPorEncharcamento() {
        assertThat(StatusPlantioCalculos.classificarStatus(95.1, CAD, 10.0)).isEqualTo(StatusPlantio.VERMELHO);
    }

    @Test
    void faixaVerdeInclusiveComChuvaPrevistaSuficiente() {
        assertThat(StatusPlantioCalculos.classificarStatus(60.0, CAD, 5.0)).isEqualTo(StatusPlantio.VERDE);
        assertThat(StatusPlantioCalculos.classificarStatus(90.0, CAD, 5.0)).isEqualTo(StatusPlantio.VERDE);
    }

    @Test
    void faixaVerdeSemChuvaSuficienteCaiParaAmareloFallbackConservador() {
        assertThat(StatusPlantioCalculos.classificarStatus(70.0, CAD, 4.9)).isEqualTo(StatusPlantio.AMARELO);
    }

    @Test
    void faixaEntre90E95PctSemChuvaEAmarelo() {
        assertThat(StatusPlantioCalculos.classificarStatus(92.0, CAD, 100.0)).isEqualTo(StatusPlantio.AMARELO);
    }
}
