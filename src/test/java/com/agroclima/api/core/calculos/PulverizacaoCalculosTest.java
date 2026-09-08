package com.agroclima.api.core.calculos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PulverizacaoCalculosTest {

    @Test
    void converteMsParaKmh() {
        assertThat(PulverizacaoCalculos.converterMsParaKmh(10.0)).isCloseTo(36.0, within(1e-9));
    }

    @ParameterizedTest
    @CsvSource({
            "3.0, 0.0, FAVORAVEL",
            "10.0, 15.0, FAVORAVEL",
            "5.0, 5.0, FAVORAVEL",
    })
    void janelaFavoravelNosLimitesInclusivos(double vento, double rajada, ClassificacaoPulverizacao esperado) {
        assertThat(PulverizacaoCalculos.classificarPulverizacao(vento, rajada)).isEqualTo(esperado);
    }

    @Test
    void ventoAcimaDe10BloqueiaPorVentoForte() {
        assertThat(PulverizacaoCalculos.classificarPulverizacao(10.1, 0.0))
                .isEqualTo(ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE);
    }

    @Test
    void rajadaAcimaDe15BloqueiaPorVentoForte() {
        assertThat(PulverizacaoCalculos.classificarPulverizacao(5.0, 15.1))
                .isEqualTo(ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE);
    }

    @Test
    void ventoAbaixoDe3BloqueiaPorInversaoTermica() {
        assertThat(PulverizacaoCalculos.classificarPulverizacao(2.9, 0.0))
                .isEqualTo(ClassificacaoPulverizacao.BLOQUEIO_INVERSAO_TERMICA);
    }

    @Test
    void ventoFortePrevaleceSobreInversaoTermicaNoOverlap() {
        // vento < 3 (dispararia inversao termica) MAS rajada > 15 (dispara vento forte) --
        // ordem de checagem e load-bearing: vento forte tem que vencer.
        assertThat(PulverizacaoCalculos.classificarPulverizacao(2.0, 20.0))
                .isEqualTo(ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE);
    }

    @Test
    void deltaTFavoravelNosLimitesInclusivos() {
        assertThat(PulverizacaoCalculos.classificarDeltaT(2.0)).isEqualTo(ClassificacaoPulverizacao.FAVORAVEL);
        assertThat(PulverizacaoCalculos.classificarDeltaT(10.0)).isEqualTo(ClassificacaoPulverizacao.FAVORAVEL);
    }

    @Test
    void deltaTAcimaDe10BloqueiaPorEvaporacaoExcessiva() {
        assertThat(PulverizacaoCalculos.classificarDeltaT(10.1))
                .isEqualTo(ClassificacaoPulverizacao.BLOQUEIO_EVAPORACAO_EXCESSIVA);
    }

    @Test
    void deltaTAbaixoDe2BloqueiaPorInversaoTermica() {
        assertThat(PulverizacaoCalculos.classificarDeltaT(1.9))
                .isEqualTo(ClassificacaoPulverizacao.BLOQUEIO_INVERSAO_TERMICA);
    }
}
