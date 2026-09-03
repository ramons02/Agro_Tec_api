# Implementation Plan: Motor de Regras e Alerta de Janela Segura para Pulverização

**Branch**: `009-motor-regras-pulverizacao` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-motor-regras-pulverizacao/spec.md`

## Summary

Função pura que classifica vento/rajada em `FAVORAVEL` / `BLOQUEIO_VENTO_FORTE` / `BLOQUEIO_INVERSAO_TERMICA` (RN001-RN003), exposta via endpoint que consome a leitura da estação mais próxima (feature 006/008) do talhão consultado.

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI, Pydantic v2 (sem dependência externa para o cálculo em si — é lógica pura)

**Storage**: N/A (função pura); consome dado já persistido pela feature 002/008

**Testing**: pytest com testes de tabela cobrindo todos os valores de fronteira (3, 10, 15 km/h)

**Target Platform**: Linux server (container)

**Project Type**: web-service (backend API)

**Performance Goals**: classificação instantânea (função pura, sem I/O) — o tempo de resposta do endpoint é dominado pela busca de dado climático (feature 008)

**Constraints**: nenhuma dependência de variação de temperatura (RN003 já resolvida) — não introduzir essa lógica

**Scale/Scope**: uma classificação por consulta de talhão

## Constitution Check

| Princípio | Avaliação |
|---|---|
| I. Assíncrono e Tipado | PASS — endpoint assíncrono; a função de classificação em si é síncrona por ser pura/sem I/O, tipada com Pydantic/typing |
| II. Custo Zero em Integrações Externas | N/A |
| III. Tempo Real sem Cache Obsoleto | PASS — consome sempre a leitura mais recente via feature 008 |
| IV. Geoprocessamento Correto e Verificável | N/A |
| V. Segurança JWT e Segredos Fora do Git | PASS — rota autenticada |

Nenhuma violação. Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/009-motor-regras-pulverizacao/
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
│       └── pulverizacao.py       # classificar_pulverizacao(vento_kmh, rajada_kmh) -> Enum — função pura
└── api/v1/endpoints/
    └── pulverizacao.py            # GET /talhoes/{id}/pulverizacao

tests/
└── unit/
    └── test_calculos_pulverizacao.py   # tabela de casos incluindo fronteiras exatas
```

**Structure Decision**: lógica de classificação isolada como função pura em `core/calculos/`, espelhando exatamente o padrão já validado no protótipo (`regrasPulverizacao.ts`), reaproveitável e testável sem qualquer I/O.

## Complexity Tracking

*Sem violações de constituição a justificar.*
