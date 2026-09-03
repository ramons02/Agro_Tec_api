import pytest

from app.core.calculos.pulverizacao import (
    ClassificacaoPulverizacao,
    classificar_delta_t,
    classificar_pulverizacao,
)


@pytest.mark.parametrize(
    ("vento_kmh", "rajada_kmh", "esperado"),
    [
        # Favoravel: 3.0 <= vento <= 10.0 e rajada <= 15.0
        (7.0, 10.0, ClassificacaoPulverizacao.FAVORAVEL),
        (3.0, 15.0, ClassificacaoPulverizacao.FAVORAVEL),  # fronteira inferior inclusiva
        (10.0, 15.0, ClassificacaoPulverizacao.FAVORAVEL),  # fronteira superior inclusiva
        # Bloqueio por vento forte: vento > 10.0 ou rajada > 15.0
        (11.0, 10.0, ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE),
        (5.0, 16.0, ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE),
        (10.1, 15.0, ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE),
        (10.0, 15.1, ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE),
        # Bloqueio por inversao termica: vento < 3.0 (sozinho, sem variacao de temperatura)
        (2.9, 5.0, ClassificacaoPulverizacao.BLOQUEIO_INVERSAO_TERMICA),
        (0.0, 0.0, ClassificacaoPulverizacao.BLOQUEIO_INVERSAO_TERMICA),
        # Caso de borda: vento<3 E rajada>15 simultaneos -> vento forte prevalece
        # (mesma ordem de checagem do prototipo regrasPulverizacao.ts, ja validado)
        (2.0, 20.0, ClassificacaoPulverizacao.BLOQUEIO_VENTO_FORTE),
    ],
)
def test_classificar_pulverizacao(vento_kmh, rajada_kmh, esperado):
    assert classificar_pulverizacao(vento_kmh, rajada_kmh) == esperado


@pytest.mark.parametrize(
    ("delta_t_c", "esperado"),
    [
        (2.0, ClassificacaoPulverizacao.FAVORAVEL),  # fronteira inferior inclusiva
        (10.0, ClassificacaoPulverizacao.FAVORAVEL),  # fronteira superior inclusiva
        (6.0, ClassificacaoPulverizacao.FAVORAVEL),
        (1.9, ClassificacaoPulverizacao.BLOQUEIO_INVERSAO_TERMICA),
        (0.0, ClassificacaoPulverizacao.BLOQUEIO_INVERSAO_TERMICA),
        (10.1, ClassificacaoPulverizacao.BLOQUEIO_EVAPORACAO_EXCESSIVA),
        (15.0, ClassificacaoPulverizacao.BLOQUEIO_EVAPORACAO_EXCESSIVA),
    ],
)
def test_classificar_delta_t(delta_t_c, esperado):
    assert classificar_delta_t(delta_t_c) == esperado
