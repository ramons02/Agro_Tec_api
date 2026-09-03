import pytest

from app.core.calculos.idw import interpolar_idw


def test_interpolar_idw_media_ponderada_por_distancia():
    # Estação a 1km pesa muito mais que estação a 10km
    resultado = interpolar_idw([(10.0, 1.0), (20.0, 10.0)])
    # peso1 = 1/1 = 1; peso2 = 1/100 = 0.01
    esperado = (10.0 * 1 + 20.0 * 0.01) / (1 + 0.01)
    assert resultado == pytest.approx(esperado)


def test_interpolar_idw_distancias_iguais_e_media_simples():
    resultado = interpolar_idw([(10.0, 5.0), (20.0, 5.0), (30.0, 5.0)])
    assert resultado == pytest.approx(20.0)


def test_interpolar_idw_distancia_zero_retorna_valor_direto():
    resultado = interpolar_idw([(15.0, 0.0), (100.0, 5.0)])
    assert resultado == 15.0


def test_interpolar_idw_uma_unica_estacao():
    resultado = interpolar_idw([(7.5, 3.0)])
    assert resultado == pytest.approx(7.5)


def test_interpolar_idw_lista_vazia_levanta_erro():
    with pytest.raises(ValueError):
        interpolar_idw([])
