package com.agroclima.api.core.calculos;

import com.agroclima.api.business.talhao.TipoSolo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SoloCalculosTest {

    @Test
    void argilaAcimaDe35PctEArgiloso() {
        assertThat(SoloCalculos.classificarTextura(35.0, 20.0, 45.0)).isEqualTo(TipoSolo.ARGILOSO);
    }

    @Test
    void areiaAcimaDe70ComArgilaAbaixoDe15EArenoso() {
        assertThat(SoloCalculos.classificarTextura(10.0, 70.0, 20.0)).isEqualTo(TipoSolo.ARENOSO);
    }

    @Test
    void areiaAcimaDe70ComArgilaAcimaDe15CaiParaMisto() {
        assertThat(SoloCalculos.classificarTextura(20.0, 75.0, 5.0)).isEqualTo(TipoSolo.MISTO);
    }

    @Test
    void demaisCombinacoesSaoMisto() {
        assertThat(SoloCalculos.classificarTextura(20.0, 40.0, 40.0)).isEqualTo(TipoSolo.MISTO);
    }

    @Test
    void calculaCadComProfundidadePadrao() {
        // CC=30%, PMP=15%, densidade=1.3 g/cm3, z=200mm -> (0.30-0.15)*1.3*200 = 39.0
        double cad = SoloCalculos.calcularCad(30.0, 15.0, 1.3);
        assertThat(cad).isCloseTo(39.0, within(1e-9));
    }

    @Test
    void estimarCcPmpRetornaCcMaiorQuePmp() {
        SoloCalculos.CcPmp resultado = SoloCalculos.estimarCcPmp(30.0, 40.0, 2.0);
        assertThat(resultado.ccPct()).isGreaterThan(resultado.pmpPct());
    }

    @Test
    void estimarCcPmpNuncaRetornaPmpNegativo() {
        // valores extremos que empurrariam o pmp bruto pra negativo antes do floor.
        SoloCalculos.CcPmp resultado = SoloCalculos.estimarCcPmp(0.0, 100.0, 0.0);
        assertThat(resultado.pmpPct()).isGreaterThanOrEqualTo(0.0);
    }
}
