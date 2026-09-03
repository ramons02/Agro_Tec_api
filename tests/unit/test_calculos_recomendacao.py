import pytest

from app.core.calculos.pulverizacao import ClassificacaoPulverizacao
from app.core.calculos.recomendacao import (
    Prioridade,
    TendenciaUmidade,
    calcular_tendencia_umidade,
    gerar_recomendacao,
)
from app.core.calculos.status_plantio import StatusPlantio

# --- RN011/RN012/RN013 — matriz de prioridade -------------------------------


@pytest.mark.parametrize(
    "status_plantio,status_pulverizacao,prioridade_esperada",
    [
        # RN011 — Vermelho é sempre ALTA, independente da pulverização.
        (StatusPlantio.VERMELHO, ClassificacaoPulverizacao.FAVORAVEL, Prioridade.ALTA),
        (StatusPlantio.VERMELHO, ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE, Prioridade.ALTA),
        (StatusPlantio.VERMELHO, None, Prioridade.ALTA),
        # RN012 — Amarelo é MEDIA mesmo com pulverização favorável.
        (StatusPlantio.AMARELO, ClassificacaoPulverizacao.FAVORAVEL, Prioridade.MEDIA),
        # RN012 — Verde + pulverização bloqueada (qualquer motivo) é MEDIA.
        (StatusPlantio.VERDE, ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE, Prioridade.MEDIA),
        (StatusPlantio.VERDE, ClassificacaoPulverizacao.BLOQUEIO_INVERSAO_TERMICA, Prioridade.MEDIA),
        (StatusPlantio.VERDE, ClassificacaoPulverizacao.BLOQUEIO_EVAPORACAO_EXCESSIVA, Prioridade.MEDIA),
        # RN013 — Verde + pulverização favorável (ou sem dado) é BAIXA.
        (StatusPlantio.VERDE, ClassificacaoPulverizacao.FAVORAVEL, Prioridade.BAIXA),
        (StatusPlantio.VERDE, None, Prioridade.BAIXA),
        (None, None, Prioridade.BAIXA),
    ],
)
def test_prioridade_por_combinacao(status_plantio, status_pulverizacao, prioridade_esperada):
    recomendacao = gerar_recomendacao(status_plantio, status_pulverizacao)
    assert recomendacao.prioridade == prioridade_esperada


def test_aviso_fixo_sempre_presente():
    recomendacao = gerar_recomendacao(StatusPlantio.VERDE, ClassificacaoPulverizacao.FAVORAVEL)
    assert recomendacao.aviso == (
        "Sugestão gerada automaticamente — não substitui avaliação agronômica profissional."
    )


# --- RN019 — tendência de umidade --------------------------------------------


def test_tendencia_subindo_acima_do_limiar():
    # 2 p.p. de CAD=100mm em 3 dias.
    tendencia = calcular_tendencia_umidade(62.0, 60.0, cad_mm=100.0)
    assert tendencia == TendenciaUmidade.SUBINDO


def test_tendencia_caindo_abaixo_do_limiar():
    tendencia = calcular_tendencia_umidade(58.0, 60.0, cad_mm=100.0)
    assert tendencia == TendenciaUmidade.CAINDO


def test_tendencia_estavel_dentro_do_limiar():
    # 0,5 p.p. — abaixo do limiar de 1,5 p.p. (RN019).
    tendencia = calcular_tendencia_umidade(60.5, 60.0, cad_mm=100.0)
    assert tendencia == TendenciaUmidade.ESTAVEL


def test_tendencia_exatamente_no_limiar_conta_como_subindo():
    tendencia = calcular_tendencia_umidade(61.5, 60.0, cad_mm=100.0)
    assert tendencia == TendenciaUmidade.SUBINDO


def test_tendencia_com_cad_zero_e_estavel():
    tendencia = calcular_tendencia_umidade(10.0, 5.0, cad_mm=0.0)
    assert tendencia == TendenciaUmidade.ESTAVEL


# --- FR-005 — tendência só afeta o texto quando status é Amarelo ------------


def test_tendencia_so_aparece_no_texto_quando_amarelo():
    subindo = gerar_recomendacao(
        StatusPlantio.AMARELO, ClassificacaoPulverizacao.FAVORAVEL, TendenciaUmidade.SUBINDO
    )
    caindo = gerar_recomendacao(
        StatusPlantio.AMARELO, ClassificacaoPulverizacao.FAVORAVEL, TendenciaUmidade.CAINDO
    )
    assert "melhorando" in subindo.texto
    assert "piorando" in caindo.texto


def test_tendencia_nao_afeta_texto_quando_vermelho():
    texto_subindo = gerar_recomendacao(
        StatusPlantio.VERMELHO, None, TendenciaUmidade.SUBINDO
    ).texto
    texto_caindo = gerar_recomendacao(
        StatusPlantio.VERMELHO, None, TendenciaUmidade.CAINDO
    ).texto
    assert texto_subindo == texto_caindo


def test_sem_dado_de_pulverizacao_e_sinalizado_no_texto():
    recomendacao = gerar_recomendacao(StatusPlantio.VERDE, None)
    assert "sem dado de pulverização" in recomendacao.texto.lower()
