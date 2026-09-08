package com.agroclima.api.core.calculos;

import com.agroclima.api.core.calculos.RecomendacaoCalculos.Recomendacao;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RecomendacaoCalculosTest {

    @Test
    void vermelhoESempreAltaIndependenteDaPulverizacao() {
        Recomendacao r = RecomendacaoCalculos.gerarRecomendacao(StatusPlantio.VERMELHO, ClassificacaoPulverizacao.FAVORAVEL);
        assertThat(r.prioridade()).isEqualTo(Prioridade.ALTA);
    }

    @Test
    void amareloEMedia() {
        Recomendacao r = RecomendacaoCalculos.gerarRecomendacao(StatusPlantio.AMARELO, ClassificacaoPulverizacao.FAVORAVEL);
        assertThat(r.prioridade()).isEqualTo(Prioridade.MEDIA);
    }

    @Test
    void verdeComPulverizacaoBloqueadaEMedia() {
        Recomendacao r = RecomendacaoCalculos.gerarRecomendacao(StatusPlantio.VERDE, ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE);
        assertThat(r.prioridade()).isEqualTo(Prioridade.MEDIA);
    }

    @Test
    void verdeComPulverizacaoFavoravelEBaixa() {
        Recomendacao r = RecomendacaoCalculos.gerarRecomendacao(StatusPlantio.VERDE, ClassificacaoPulverizacao.FAVORAVEL);
        assertThat(r.prioridade()).isEqualTo(Prioridade.BAIXA);
    }

    @Test
    void avisoFixoSemprePresente() {
        Recomendacao r = RecomendacaoCalculos.gerarRecomendacao(StatusPlantio.VERDE, ClassificacaoPulverizacao.FAVORAVEL);
        assertThat(r.aviso()).isEqualTo(RecomendacaoCalculos.AVISO_FIXO);
    }

    @Test
    void semStatusPlantioUsaTextoDeAusenciaDeCalculo() {
        Recomendacao r = RecomendacaoCalculos.gerarRecomendacao(null, ClassificacaoPulverizacao.FAVORAVEL);
        assertThat(r.texto()).contains("Ainda sem balanço hídrico calculado");
    }

    @Test
    void semDadoDePulverizacaoUsaTextoDeAusencia() {
        Recomendacao r = RecomendacaoCalculos.gerarRecomendacao(StatusPlantio.VERDE, null);
        assertThat(r.texto()).contains("Sem dado de pulverização disponível");
    }

    @Test
    void tendenciaSoAfetaTextoQuandoStatusEAmarelo() {
        Recomendacao subindo = RecomendacaoCalculos.gerarRecomendacao(
                StatusPlantio.AMARELO, ClassificacaoPulverizacao.FAVORAVEL, TendenciaUmidade.SUBINDO);
        Recomendacao caindo = RecomendacaoCalculos.gerarRecomendacao(
                StatusPlantio.AMARELO, ClassificacaoPulverizacao.FAVORAVEL, TendenciaUmidade.CAINDO);
        assertThat(subindo.texto()).isNotEqualTo(caindo.texto());

        Recomendacao verdeSubindo = RecomendacaoCalculos.gerarRecomendacao(
                StatusPlantio.VERDE, ClassificacaoPulverizacao.FAVORAVEL, TendenciaUmidade.SUBINDO);
        Recomendacao verdeCaindo = RecomendacaoCalculos.gerarRecomendacao(
                StatusPlantio.VERDE, ClassificacaoPulverizacao.FAVORAVEL, TendenciaUmidade.CAINDO);
        assertThat(verdeSubindo.texto()).isEqualTo(verdeCaindo.texto());
    }

    @Test
    void tendenciaUmidadeLimiteInclusivo1e5PontosPercentuais() {
        assertThat(RecomendacaoCalculos.calcularTendenciaUmidade(51.5, 50.0, 100.0)).isEqualTo(TendenciaUmidade.SUBINDO);
        assertThat(RecomendacaoCalculos.calcularTendenciaUmidade(48.5, 50.0, 100.0)).isEqualTo(TendenciaUmidade.CAINDO);
        assertThat(RecomendacaoCalculos.calcularTendenciaUmidade(50.0, 50.0, 100.0)).isEqualTo(TendenciaUmidade.ESTAVEL);
    }

    @Test
    void cadZeroOuNegativaSempreEstavel() {
        assertThat(RecomendacaoCalculos.calcularTendenciaUmidade(80.0, 20.0, 0.0)).isEqualTo(TendenciaUmidade.ESTAVEL);
    }
}
