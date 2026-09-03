# Implementation Plan: Ingestão Assíncrona de Dados das Estações do INMET

**Branch**: `002-ingestao-inmet` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-ingestao-inmet/spec.md`

## Summary

Job assíncrono periódico que busca as medições mais recentes das estações automáticas do INMET no Pará via `httpx`, persiste em `medicoes_clima`, e aciona fallback para o Open-Meteo (feature 003) quando o INMET não responder em até 3 segundos. Idempotente por (estação, instante) para evitar duplicidade.

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI (para expor status/trigger manual), httpx (cliente assíncrono), APScheduler (agendamento em segundo plano), SQLAlchemy Async + asyncpg

**Storage**: PostgreSQL (`estacoes_inmet`, `medicoes_clima`)

**Testing**: pytest + pytest-asyncio, com mock de `httpx` para simular respostas/timeout do INMET

**Target Platform**: Linux server (container), job em segundo plano no mesmo processo ou worker dedicado

**Project Type**: web-service (backend API + worker assíncrono)

**Performance Goals**: fallback acionado em até 3 segundos de timeout do INMET (RN009); ingestão não bloqueia requisições HTTP concorrentes

**Constraints**: custo zero de bilhetagem (Princípio II); retenção de granularidade horária limitada a 12 meses, com agregação diária depois (RNF014)

**Scale/Scope**: todas as estações automáticas do INMET localizadas no Pará (dezenas de estações)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação |
|---|---|
| I. Assíncrono e Tipado | PASS — chamada HTTP ao INMET via `httpx` assíncrono; persistência via SQLAlchemy Async |
| II. Custo Zero em Integrações Externas | PASS — INMET é aberto e gratuito; nenhuma chave paga envolvida |
| III. Tempo Real sem Cache Obsoleto | PASS — é a própria fonte de dado "ao vivo" consumida pela feature 008 |
| IV. Geoprocessamento Correto e Verificável | N/A — estações já têm posição fixa (SRID 4326), esta feature só grava medições, não recalcula geometria |
| V. Segurança JWT e Segredos Fora do Git | N/A — feature é um job interno, não expõe rota de escrita externa |

Nenhuma violação. Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/002-ingestao-inmet/
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
│   └── inmet_service.py         # cliente httpx + parsing da resposta do INMET
├── db/
│   └── models/
│       ├── estacao_inmet.py
│       └── medicao_clima.py
├── core/
│   └── scheduler.py              # registro do job periódico (APScheduler)
└── api/v1/endpoints/
    └── ingestao.py                # endpoint opcional de status/trigger manual (uso interno/debug)

tests/
├── contract/
│   └── test_inmet_service.py
└── unit/
    └── test_fallback_timeout.py
```

**Structure Decision**: projeto único, reaproveitando `app/services/` para os conectores externos, conforme Convenção de Desenvolvimento.

## Complexity Tracking

*Sem violações de constituição a justificar.*
