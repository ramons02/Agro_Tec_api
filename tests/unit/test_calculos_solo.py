from app.core.calculos.solo import calcular_cad, classificar_textura, estimar_cc_pmp
from app.db.models.talhao import TipoSolo


def test_classificar_textura_argilosa():
    assert classificar_textura(argila_pct=40, areia_pct=30, silte_pct=30) == TipoSolo.ARGILOSO


def test_classificar_textura_arenosa():
    assert classificar_textura(argila_pct=10, areia_pct=75, silte_pct=15) == TipoSolo.ARENOSO


def test_classificar_textura_mista():
    assert classificar_textura(argila_pct=20, areia_pct=40, silte_pct=40) == TipoSolo.MISTO


def test_classificar_textura_fronteira_argila_35_e_argiloso():
    assert classificar_textura(argila_pct=35, areia_pct=30, silte_pct=35) == TipoSolo.ARGILOSO


def test_classificar_textura_areia_alta_mas_argila_impede_arenoso():
    # areia >= 70% mas argila >= 15% -> não é ARENOSO (mistura incomum, mas a regra é essa)
    assert classificar_textura(argila_pct=16, areia_pct=75, silte_pct=9) == TipoSolo.MISTO


def test_calcular_cad_formula_rn020():
    # CC=40%, PMP=15%, densidade=1.3 g/cm3, z=200mm -> (0.40-0.15)*1.3*200 = 65.0
    cad = calcular_cad(
        capacidade_campo_pct=40, ponto_murcha_permanente_pct=15, densidade_solo_g_cm3=1.3
    )
    assert round(cad, 2) == 65.0


def test_calcular_cad_usa_profundidade_customizada():
    cad_200mm = calcular_cad(40, 15, 1.3, profundidade_raizes_mm=200)
    cad_400mm = calcular_cad(40, 15, 1.3, profundidade_raizes_mm=400)
    assert round(cad_400mm, 2) == round(cad_200mm * 2, 2)


def test_estimar_cc_pmp_retorna_cc_maior_que_pmp():
    cc, pmp = estimar_cc_pmp(fracao_argila_pct=30, fracao_areia_pct=40, materia_organica_pct=2.0)
    assert cc > pmp > 0


def test_estimar_cc_pmp_solo_arenoso_retem_menos_agua_que_argiloso():
    cc_arenoso, _ = estimar_cc_pmp(fracao_argila_pct=5, fracao_areia_pct=85, materia_organica_pct=1.0)
    cc_argiloso, _ = estimar_cc_pmp(fracao_argila_pct=50, fracao_areia_pct=15, materia_organica_pct=1.0)
    assert cc_arenoso < cc_argiloso
