# Quickstart: Parametrização Automática de Solo via SoilGrids

## Cenário 1 — Talhão com cobertura de dado

Cadastrar um talhão com coordenada central conhecida no Pará (feature 005) e consultar o talhão criado.

**Esperado**: `tipo_solo` preenchido com um dos três valores válidos e `capacidade_agua_disponivel_mm` com um número positivo.

## Cenário 2 — Talhão sem cobertura

Cadastrar um talhão com coordenada fora da cobertura da fonte (simulação em teste).

**Esperado**: o talhão é salvo normalmente, com `tipo_solo` e CAD nulos — o cadastro não é bloqueado.

## Validação de sucesso

Feature validada quando os dois cenários se comportam como descrito e a fórmula de CAD bate com o exemplo de referência do Escopo Técnico.
