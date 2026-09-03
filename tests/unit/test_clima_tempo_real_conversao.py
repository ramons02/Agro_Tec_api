from app.core.calculos.pulverizacao import converter_ms_para_kmh


def test_converter_ms_para_kmh():
    assert converter_ms_para_kmh(1.0) == 3.6


def test_converter_ms_para_kmh_zero():
    assert converter_ms_para_kmh(0.0) == 0.0


def test_converter_ms_para_kmh_valor_tipico_inmet():
    # 2.78 m/s ~= 10 km/h (limite superior da faixa favoravel, RN001)
    assert round(converter_ms_para_kmh(2.78), 1) == 10.0
