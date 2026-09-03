# Quickstart: Balanço Hídrico do Solo

## Cenário 1 — Cálculo dentro dos limites

Com $ARM_{i-1} = 40mm$, $P_i = 10mm$, $ET_i = 5mm$, $CAD = 60mm$: esperado $ARM_i = 45mm$.

## Cenário 2 — Limite superior (encharcamento)

Com $ARM_{i-1} = 55mm$, $P_i = 30mm$, $ET_i = 2mm$, $CAD = 60mm$: esperado $ARM_i = 60mm$ (nunca ultrapassa a CAD).

## Cenário 3 — Limite inferior (seca)

Com $ARM_{i-1} = 3mm$, $P_i = 0mm$, $ET_i = 8mm$, $CAD = 60mm$: esperado $ARM_i = 0mm$ (nunca negativo).

## Validação de sucesso

Feature validada quando os 3 cenários batem exatamente com a fórmula oficial, via teste automatizado.
