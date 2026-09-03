import pytest

from app.core.calculos.balanco_hidrico import armazenamento_inicial, calcular_armazenamento


def test_calculo_dentro_dos_limites():
    # ARM_i-1=40, P=10, ET0=12.5, Kc=0.4 -> ET_real=5.0 -> 40+10-5=45
    arm = calcular_armazenamento(
        arm_anterior_mm=40, precipitacao_mm=10, et0_mm=12.5, cad_mm=60, kc=0.4
    )
    assert arm == pytest.approx(45.0)


def test_calculo_limitado_ao_teto_da_cad():
    # 55 + 30 - (2/0.4=0.8) = 84.2, mas teto e 60
    arm = calcular_armazenamento(
        arm_anterior_mm=55, precipitacao_mm=30, et0_mm=2, cad_mm=60, kc=0.4
    )
    assert arm == 60.0


def test_calculo_limitado_ao_piso_zero():
    # 3 + 0 - (8*0.4=3.2) = -0.2, nunca negativo
    arm = calcular_armazenamento(arm_anterior_mm=3, precipitacao_mm=0, et0_mm=8, cad_mm=60, kc=0.4)
    assert arm == 0.0


def test_calculo_usa_kc_padrao_fase_inicial():
    arm_com_kc_explicito = calcular_armazenamento(40, 10, 12.5, 60, kc=0.4)
    arm_com_kc_padrao = calcular_armazenamento(40, 10, 12.5, 60)
    assert arm_com_kc_explicito == arm_com_kc_padrao


def test_armazenamento_inicial_e_fracao_da_cad():
    assert armazenamento_inicial(cad_mm=60) == pytest.approx(42.0)  # 0.70 * 60
