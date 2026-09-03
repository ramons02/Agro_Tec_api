# Implementation Plan: Identificação Espacial da Estação INMET Mais Próxima

**Branch**: `006-estacao-mais-proxima` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-estacao-mais-proxima/spec.md`

## Summary

Consulta espacial no PostGIS usando o operador `<->` para encontrar, a partir do centroide de um talhão, a estação INMET fisicamente mais próxima, retornando código, município e distância em km, em menos de 100ms.

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI, SQLAlchemy Async + GeoAlchemy2

**Storage**: PostgreSQL + PostGIS (`talhoes`, `estacoes_inmet`), com índice espacial (GiST) em ambas as colunas de geometria

**Testing**: pytest + pytest-asyncio contra banco PostGIS de teste com dataset conhecido (estações e talhões com distâncias pré-calculadas)

**Target Platform**: Linux server (container)

**Project Type**: web-service (backend API)

**Performance Goals**: resposta em menos de 100ms (RNF003)

**Constraints**: usar exclusivamente operadores nativos do PostGIS para distância (Princípio IV) — nunca haversine em código de aplicação

**Scale/Scope**: dezenas de estações, potencialmente milhares de talhões consultando

## Constitution Check

| Princípio | Avaliação |
|---|---|
| I. Assíncrono e Tipado | PASS — consulta assíncrona via SQLAlchemy Async |
| II. Custo Zero em Integrações Externas | N/A |
| III. Tempo Real sem Cache Obsoleto | N/A — geometria de estação/talhão não muda em tempo real |
| IV. Geoprocessamento Correto e Verificável | PASS — é o núcleo desta feature: operador `<->` nativo, índice GiST obrigatório para atingir <100ms |
| V. Segurança JWT e Segredos Fora do Git | PASS — rota exige autenticação (feature 001) |

Nenhuma violação. Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/006-estacao-mais-proxima/
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
├── api/v1/endpoints/
│   └── talhoes.py                # GET /talhoes/{id}/estacao-mais-proxima (extensão do endpoint da feature 005)
└── db/
    └── queries/
        └── estacao_proxima.py    # query SQL/ORM usando <->, com índice GiST

tests/
└── contract/
    └── test_estacao_mais_proxima.py
```

**Structure Decision**: adicionada como uma rota/consulta sobre a mesma entidade `Talhao` já modelada na feature 005 — não introduz novo agregado de dados.

## Complexity Tracking

*Sem violações de constituição a justificar.*
