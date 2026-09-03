# Quickstart: Motor de Regras de Pulverização

## Cenário 1 — Favorável

Vento = 7 km/h, rajada = 10 km/h → `FAVORAVEL`.

## Cenário 2 — Bloqueio por vento forte

Vento = 11 km/h → `BLOQUEIO_VENTO_FORTE`. Também: vento = 5 km/h, rajada = 16 km/h → `BLOQUEIO_VENTO_FORTE`.

## Cenário 3 — Bloqueio por inversão térmica

Vento = 2 km/h (qualquer rajada) → `BLOQUEIO_INVERSAO_TERMICA`.

## Cenário 4 — Fronteiras exatas

Vento = 3 km/h → `FAVORAVEL` (inclusive). Vento = 10 km/h, rajada = 15 km/h → `FAVORAVEL` (inclusive).

## Validação de sucesso

Feature validada quando os 4 cenários (incluindo fronteiras) retornam exatamente a classificação esperada, via teste automatizado de tabela.
