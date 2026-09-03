# Tasks: Exportacao de Relatorio de Talhoes

**Input**: Design documents from `/specs/015-exportacao-relatorio-talhoes/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md; feature 011 (dashboard) implementada

**Tests**: nao solicitados explicitamente — omitidos; validacao via `quickstart.md`.

## Phase 1: Setup

*Sem dependencia nova — usa modulo `csv` da stdlib.*

## Phase 2: Foundational

*Reaproveita consulta filtrada da feature 011 — sem novo schema.*

## Phase 3: User Story 1 - Exportar talhoes filtrados em CSV (Priority: P1) 🎯 MVP

**Goal**: CSV com exatamente os talhoes filtrados, BOM UTF-8.

**Independent Test**: Cenarios 1-2 do `quickstart.md`.

### Implementation for User Story 1

- [ ] T001 [US1] Implementar `app/services/exportacao_csv_service.py`: reaproveita query filtrada da feature 011 (sem paginacao), monta linhas CSV (propriedade, talhao, area_ha, tipo_solo, status_plantio, umidade_0_7cm_pct)
- [ ] T002 [US1] Implementar `GET /api/v1/dashboard/plantio/exportar.csv` (contrato `contracts/exportacao-csv.md`): `Content-Type: text/csv; charset=utf-8`, BOM UTF-8, `Content-Disposition: attachment`
- [ ] T003 [US1] Aplicar mesmo escopo RBAC (feature 014) e mesmos filtros (`propriedade_id`, `status`) do dashboard

**Checkpoint**: exportacao completa, historia unica.

---

## Phase Final: Polish

- [ ] T004 [P] Teste de encoding (BOM presente, acentuacao correta) em `tests/contract/test_exportacao_csv.py`
- [ ] T005 Rodar Cenarios 1-2 do `quickstart.md`

## Dependencies & Execution Order

Foundational → US1 → Polish

## Implementation Strategy

Feature de historia unica, prioridade Baixa no MVP geral (RF034) — pode ser a ultima das 15 a ser implementada sem bloquear nada.
