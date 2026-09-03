import pytest

from app.core.calculos.psicrometria import calcular_bulbo_umido, calcular_delta_t


def test_bulbo_umido_nunca_maior_que_bulbo_seco():
    for temp in (15, 20, 25, 30, 35):
        for umidade in (10, 30, 50, 70, 90):
            assert calcular_bulbo_umido(temp, umidade) <= temp + 0.01  # tolerância numérica


def test_bulbo_umido_igual_ao_seco_quando_umidade_100pct():
    # Com ar saturado (RH=100%), bulbo úmido converge para o bulbo seco.
    temp = 25.0
    bulbo_umido = calcular_bulbo_umido(temp, 100.0)
    assert bulbo_umido == pytest.approx(temp, abs=0.5)


def test_delta_t_diminui_com_aumento_de_umidade():
    delta_t_seco = calcular_delta_t(30.0, 30.0)
    delta_t_umido = calcular_delta_t(30.0, 80.0)
    assert delta_t_umido < delta_t_seco


def test_delta_t_proximo_de_zero_com_umidade_saturada():
    delta_t = calcular_delta_t(25.0, 100.0)
    assert delta_t == pytest.approx(0.0, abs=0.5)


def test_delta_t_positivo_com_ar_seco():
    delta_t = calcular_delta_t(35.0, 20.0)
    assert delta_t > 5.0
