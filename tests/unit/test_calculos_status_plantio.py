import pytest

from app.core.calculos.status_plantio import StatusPlantio, classificar_status

CAD = 100.0


@pytest.mark.parametrize(
    ("armazenamento_mm", "chuva_prevista_mm", "esperado"),
    [
        # Vermelho: seca (<30%) ou encharcamento (>95%)
        (29.9, 10.0, StatusPlantio.VERMELHO),
        (0.0, 10.0, StatusPlantio.VERMELHO),
        (95.1, 10.0, StatusPlantio.VERMELHO),
        (100.0, 10.0, StatusPlantio.VERMELHO),
        # Verde: 60-90% CAD E chuva prevista >= 5mm
        (60.0, 5.0, StatusPlantio.VERDE),  # fronteira inferior inclusiva
        (90.0, 5.0, StatusPlantio.VERDE),  # fronteira superior inclusiva
        (75.0, 10.0, StatusPlantio.VERDE),
        # Amarelo: faixa classica 30-60%
        (30.0, 10.0, StatusPlantio.AMARELO),
        (59.9, 10.0, StatusPlantio.AMARELO),
        # Amarelo: fallback conservador - faixa implicita 90-95% CAD
        (92.0, 10.0, StatusPlantio.AMARELO),
        (95.0, 10.0, StatusPlantio.AMARELO),
        # Amarelo: fallback conservador - 60-90% CAD mas sem os 5mm de chuva prevista
        (75.0, 4.9, StatusPlantio.AMARELO),
        (75.0, 0.0, StatusPlantio.AMARELO),
    ],
)
def test_classificar_status(armazenamento_mm, chuva_prevista_mm, esperado):
    assert classificar_status(armazenamento_mm, CAD, chuva_prevista_mm) == esperado
