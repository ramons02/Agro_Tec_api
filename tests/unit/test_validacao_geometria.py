from shapely.geometry import Point, Polygon

from app.core.geo.validacao_geometria import esta_dentro_do_para, geometria_valida

TALHAO_VALIDO_NO_PARA = Polygon(
    [(-48.50, -1.46), (-48.49, -1.46), (-48.49, -1.45), (-48.50, -1.45)]
)


def test_esta_dentro_do_para_aceita_centroide_dentro_da_bbox():
    assert esta_dentro_do_para(TALHAO_VALIDO_NO_PARA.centroid) is True


def test_esta_dentro_do_para_rejeita_centroide_fora_da_bbox():
    ponto_em_sao_paulo = Point(-46.63, -23.55)
    assert esta_dentro_do_para(ponto_em_sao_paulo) is False


def test_esta_dentro_do_para_fronteira_inclusiva():
    ponto_na_fronteira = Point(-59.0, -9.9)
    assert esta_dentro_do_para(ponto_na_fronteira) is True


def test_geometria_valida_aceita_poligono_simples():
    assert geometria_valida(TALHAO_VALIDO_NO_PARA) is True


def test_geometria_valida_rejeita_poligono_vazio():
    assert geometria_valida(Polygon()) is False


def test_geometria_valida_rejeita_poligono_auto_intersectante():
    poligono_bowtie = Polygon([(0, 0), (1, 1), (1, 0), (0, 1)])
    assert geometria_valida(poligono_bowtie) is False
