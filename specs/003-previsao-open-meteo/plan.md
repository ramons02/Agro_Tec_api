# Implementation Plan: Integração com Open-Meteo para Previsão Climática e Solo

**Branch**: `003-previsao-open-meteo` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-previsao-open-meteo/spec.md`

## Summary

Serviço assíncrono que consulta a API Open-Meteo pela coordenada de um talhão, obtendo vento horário (10m/100m), evapotranspiração diária (ET0) e umidade do solo em 4 profundidades; estrutura a resposta em formato consumível pelo Balanço Hídrico (feature 010) e serve como fallback da Ingestão INMET (feature 002).

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI, httpx (cliente assíncrono), Pydantic v2 (schemas de resposta), cachetools ou cache em Redis/memória para conter volume de chamadas

**Storage**: N/A para esta feature isoladamente (dado é consumido on-demand); pode ser cacheado em memória/Redis por coordenada+hora

**Testing**: pytest + pytest-asyncio, mocks de resposta HTTP do Open-Meteo (payloads reais de exemplo salvos como fixtures)

**Target Platform**: Linux server (container)

**Project Type**: web-service (backend API)

**Performance Goals**: consulta em menos de 2 segundos (RNF002); volume de chamadas abaixo de 10.000/dia (RNF012)

**Constraints**: custo zero de bilhetagem (Princípio II); sem cartão de crédito exigido pelo provedor

**Scale/Scope**: uma consulta por talhão por ciclo de atualização (potencialmente centenas de talhões no Pará no MVP)

## Constitution Check

| Princípio | Avaliação |
|---|---|
| I. Assíncrono e Tipado | PASS — chamada via `httpx` assíncrono; resposta tipada em Pydantic v2 |
| II. Custo Zero em Integrações Externas | PASS — Open-Meteo gratuito até 10.000 chamadas/dia, sem chave paga |
| III. Tempo Real sem Cache Obsoleto | PASS (parcial) — esta feature é uma fonte de dado consumida pela 008; cache aqui é para conter volume de chamadas externas, não para servir dado desatualizado ao usuário final |
| IV. Geoprocessamento Correto e Verificável | N/A — consulta usa lat/long já resolvida pelo talhão, não recalcula geometria |
| V. Segurança JWT e Segredos Fora do Git | N/A — Open-Meteo não exige chave de API |

Nenhuma violação. Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/003-previsao-open-meteo/
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
│   └── openmeteo_service.py     # cliente httpx + parsing (vento, ET0, umidade do solo)
└── api/v1/endpoints/
    └── (consumido internamente por outras features, sem endpoint público próprio)

tests/
└── contract/
    └── test_openmeteo_service.py
```

**Structure Decision**: serviço interno em `app/services/`, sem rota HTTP pública própria — é consumido pelas features de Ingestão (002, fallback), Solo (004, complementar) e Balanço Hídrico (010).

## Complexity Tracking

*Sem violações de constituição a justificar.*
