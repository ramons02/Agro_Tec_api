# Implementation Plan: Algoritmo de Balanço Hídrico do Solo para Janela de Plantio

**Branch**: `010-balanco-hidrico-solo` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-balanco-hidrico-solo/spec.md`

## Summary

Job diário (por talhão) que calcula $ARM_i = \min(CAD, \max(0, ARM_{i-1} + P_i - ET_i))$, com $ET_i = ET_0 \times K_c$, persistindo o armazenamento diário usado pelas features de Dashboard (011) e Recomendação (012).

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI (exposição opcional de leitura), SQLAlchemy Async, APScheduler (job diário)

**Storage**: PostgreSQL (`balanco_hidrico_diario`, nova tabela; lê `talhoes.capacidade_agua_disponivel_mm` da feature 004)

**Testing**: pytest com testes de tabela para a função pura de cálculo (incluindo limites 0 e CAD)

**Target Platform**: Linux server (container)

**Project Type**: web-service (backend API + job em segundo plano)

**Performance Goals**: recálculo de todos os talhões ativos concluído dentro de 24h após novos dados de P/ET0 (SC-002 da spec)

**Constraints**: resultado sempre limitado entre 0 e a CAD do talhão (Princípio I — função tipada e determinística)

**Scale/Scope**: um cálculo diário por talhão ativo

## Constitution Check

| Princípio | Avaliação |
|---|---|
| I. Assíncrono e Tipado | PASS — job assíncrono; a fórmula em si é uma função pura tipada, testável isoladamente |
| II. Custo Zero em Integrações Externas | N/A — reaproveita dados já obtidos por 002/003 |
| III. Tempo Real sem Cache Obsoleto | N/A — cálculo diário, não é rota de consulta em tempo real |
| IV. Geoprocessamento Correto e Verificável | N/A |
| V. Segurança JWT e Segredos Fora do Git | N/A — job interno |

Nenhuma violação. Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/010-balanco-hidrico-solo/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md
```

### Source Code (repository root)

```text
app/
├── core/
│   └── calculos/
│       └── balanco_hidrico.py    # calcular_armazenamento(arm_anterior, precipitacao, et0, kc, cad) -> float — função pura
├── core/
│   └── scheduler.py               # (extensão do job da feature 002) registra o job diário de balanço hídrico
└── db/models/
    └── balanco_hidrico_diario.py  # talhao_id, data, armazenamento_mm

tests/
└── unit/
    └── test_calculos_balanco_hidrico.py
```

**Structure Decision**: fórmula isolada como função pura em `core/calculos/`, mesmo padrão da feature 009, reaproveitando diretamente a lógica já validada em `balancoHidrico.ts` do protótipo.

## Complexity Tracking

*Sem violações de constituição a justificar.*
