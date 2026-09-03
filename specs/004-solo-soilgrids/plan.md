# Implementation Plan: Parametrização Automática de Solo via SoilGrids

**Branch**: `004-solo-soilgrids` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-solo-soilgrids/spec.md`

## Summary

Ao cadastrar/atualizar um talhão, consulta a API SoilGrids (ISRIC/Embrapa) pela coordenada central, extrai frações de argila/areia/silte/matéria orgânica, classifica o tipo de solo (`ARGILOSO`/`ARENOSO`/`MISTO`) e calcula/persiste a Capacidade de Água Disponível (CAD) conforme RN020.

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI, httpx, Pydantic v2

**Storage**: PostgreSQL (`talhoes.tipo_solo`, `talhoes.capacidade_campo`/CAD)

**Testing**: pytest + pytest-asyncio, fixtures com payloads reais do SoilGrids; testes unitários da fórmula de CAD e da classificação de textura com casos de fronteira

**Target Platform**: Linux server (container)

**Project Type**: web-service (backend API)

**Performance Goals**: classificação e CAD calculadas em menos de 5 segundos após o cadastro do talhão

**Constraints**: custo zero (Princípio II); talhão deve poder ser salvo mesmo sem cobertura de dado de solo (FR-006)

**Scale/Scope**: uma consulta por talhão cadastrado (evento pontual, não recorrente)

## Constitution Check

| Princípio | Avaliação |
|---|---|
| I. Assíncrono e Tipado | PASS — chamada httpx assíncrona; fórmula de CAD tipada e testável isoladamente |
| II. Custo Zero em Integrações Externas | PASS — SoilGrids é dado científico aberto |
| III. Tempo Real sem Cache Obsoleto | N/A — dado de solo não muda em tempo real, não está sujeito à regra de staleness de 30 min |
| IV. Geoprocessamento Correto e Verificável | PASS — usa o centroide do talhão (calculado via PostGIS na feature 005), não um cálculo aproximado próprio |
| V. Segurança JWT e Segredos Fora do Git | N/A — SoilGrids não exige chave |

Nenhuma violação. Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/004-solo-soilgrids/
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
├── services/
│   └── soilgrids_service.py     # cliente httpx + parsing de frações
├── core/
│   └── calculos/
│       └── solo.py               # classificação de textura + fórmula de CAD (função pura, testável)
└── db/models/
    └── talhao.py                  # campos tipo_solo, capacidade_agua_disponivel

tests/
└── unit/
    └── test_calculos_solo.py     # casos de fronteira da classificação e da CAD
```

**Structure Decision**: lógica de cálculo isolada em função pura (`core/calculos/solo.py`), separada do cliente HTTP, para ser testável sem rede — mesmo padrão validado no protótipo (`balancoHidrico.ts`/`regrasPulverizacao.ts` como funções puras).

## Complexity Tracking

*Sem violações de constituição a justificar.*
