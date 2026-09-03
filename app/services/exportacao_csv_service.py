"""Exportação CSV do Dashboard de Plantio (feature 015).

`data-model.md` desta feature lista "umidade 0-7cm (%)" como coluna — esse
valor não é uma medição real persistida por talhão (não existe endpoint nem
tabela para ele; é usado apenas como entrada transiente do Balanço Hídrico,
feature 010, vindo da previsão do Open-Meteo para o instante do cálculo).
Substituído aqui por `armazenamento_mm`/`percentual_cad`, que são os valores
reais e persistidos do Balanço Hídrico Diário (mesma fonte do Dashboard,
feature 011) — nunca exportar um número que pareça medição real sem ser.
"""

import csv
import io

CABECALHOS = ["Propriedade", "Talhao", "Area (ha)", "Solo", "Status", "Armazenamento (mm)", "% da CAD"]


def gerar_csv_talhoes(itens: list[dict]) -> str:
    buffer = io.StringIO()
    escritor = csv.writer(buffer, delimiter=";")
    escritor.writerow(CABECALHOS)
    for item in itens:
        escritor.writerow(
            [
                item["propriedade"],
                item["nome"],
                f"{item['area_ha']:.1f}",
                item["tipo_solo"] or "",
                item["status_plantio"] or "SEM_CALCULO",
                f"{item['armazenamento_mm']:.1f}" if item["armazenamento_mm"] is not None else "",
                f"{item['percentual_cad']:.1f}" if item["percentual_cad"] is not None else "",
            ]
        )
    # BOM UTF-8 (FR-003) — sem ele, o Excel em português interpreta o arquivo
    # como Latin-1 e corrompe a acentuação.
    return "﻿" + buffer.getvalue()
