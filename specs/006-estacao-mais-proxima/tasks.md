# Tasks: Identificação Espacial da Estação INMET Mais Próxima

**Input**: Design documents from `/specs/006-estacao-mais-proxima/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md; features 002 (estações) e 005 (talhões) implementadas

**Tests**: não solicitados explicitamente — omitidos; validação via `quickstart.md`.

## Phase 1: Setup

*Nenhuma dependência nova — reaproveita GeoAlchemy2 já instalado pela feature 005.*

## Phase 2: Foundational

- [X] T001 [P] Criar/confirmar índice GiST em `estacoes_inmet.posicao` (pode já existir da feature 002; validar aqui)
- [X] T002 [P] Criar/confirmar índice GiST em `talhoes.geometria` (pode já existir da feature 005; validar aqui)

**Checkpoint**: índices espaciais prontos para consulta performática.

---

## Phase 3: User Story 1 - Vincular talhão à estação mais próxima (Priority: P1) 🎯 MVP

**Goal**: dado um talhão, retornar a estação INMET mais próxima com distância em km.

**Independent Test**: Cenários 1 e 2 do `quickstart.md`.

### Implementation for User Story 1

- [X] T003 [US1] Implementar `app/db/queries/estacao_proxima.py`: query usando `ORDER BY posicao <-> ST_Centroid(geometria) LIMIT 1`, com `ST_Distance(..::geography)` para distância real em metros
- [X] T004 [US1] Implementar endpoint `GET /api/v1/talhoes/{id}/estacao-mais-proxima` (contrato em `contracts/estacao-mais-proxima.md`), protegido por autenticação
- [X] T005 [US1] Tratar talhão inexistente ou sem estações cadastradas com HTTP 404

**Checkpoint**: consulta funcional e correta.

---

## Phase Final: Polish

- [X] T006 [P] Escrever teste com dataset de distâncias conhecidas em `tests/integration/test_estacao_mais_proxima_integration.py` (Postgres+PostGIS real, não SQLite — geometria exige PostGIS)
- [X] T007 Medir e validar tempo de resposta < 100ms (RNF003) com `EXPLAIN ANALYZE` sobre a query do T003 — resultado real: ~5.2ms com 80 estações seedadas, usando Index Scan no GiST (`idx_estacoes_inmet_posicao`), bem abaixo do limite
- [X] T008 Rodar os 2 cenários de `quickstart.md`

## Dependencies & Execution Order

Setup (N/A) → Foundational → US1 → Polish

## Parallel Example

```bash
Task: "T001 Confirmar índice GiST em estacoes_inmet"
Task: "T002 Confirmar índice GiST em talhoes"
```

## Implementation Strategy

Feature de história única (US1) — MVP e escopo completo coincidem.
