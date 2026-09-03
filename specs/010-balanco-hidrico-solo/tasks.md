# Tasks: Algoritmo de Balanço Hídrico do Solo para Janela de Plantio

**Input**: Design documents from `/specs/010-balanco-hidrico-solo/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md; features 003 (ET0/precipitação), 004 (CAD) implementadas

**Tests**: não solicitados explicitamente — incluída apenas a verificação de limites como parte central da implementação (SC-001).

## Phase 1: Setup

*Reaproveita o APScheduler já configurado pela feature 002.*

## Phase 2: Foundational

- [ ] T001 Criar modelo `BalancoHidricoDiario` em `app/db/models/` (talhao_id, data, armazenamento_mm, precipitacao_mm, evapotranspiracao_mm) conforme `data-model.md`
- [ ] T002 [P] Criar migração Alembic com índice `(talhao_id, data DESC)` e unicidade `(talhao_id, data)`

**Checkpoint**: schema pronto para armazenar o histórico diário.

---

## Phase 3: User Story 1 - Calcular o armazenamento diário de água no solo (Priority: P1) 🎯 MVP

**Goal**: cálculo diário correto e limitado entre 0 e CAD.

**Independent Test**: 3 cenários do `quickstart.md` (dentro dos limites, teto, piso).

### Implementation for User Story 1

- [ ] T003 [US1] Implementar `calcular_armazenamento(arm_anterior_mm, precipitacao_mm, et0_mm, kc, cad_mm) -> float` em `app/core/calculos/balanco_hidrico.py` (função pura, RN007), limitando ao intervalo `[0, cad_mm]`
- [ ] T004 [P] [US1] Escrever teste de tabela com os 3 cenários do `quickstart.md` em `tests/unit/test_calculos_balanco_hidrico.py`
- [ ] T005 [US1] Implementar o job diário (`app/core/scheduler.py`, novo job): para cada talhão ativo, obtém $ET_0$/precipitação (feature 003) e CAD (feature 004), calcula e persiste `BalancoHidricoDiario`
- [ ] T006 [US1] Aplicar valor inicial de $ARM_0 = 0,70 \times CAD$ para talhão sem histórico (ver research.md)
- [ ] T007 [US1] Aplicar $K_c$ fixo da fase inicial (ver research.md) no cálculo de $ET_i = ET_0 \times K_c$

**Checkpoint**: job diário funcional, produzindo histórico correto por talhão.

---

## Phase Final: Polish

- [ ] T008 [P] Implementar endpoint de leitura `GET /api/v1/talhoes/{id}/balanco-hidrico` (contrato em `contracts/balanco-hidrico.md`), útil para debug/consumo direto
- [ ] T009 Rodar os 3 cenários de `quickstart.md`

## Dependencies & Execution Order

Foundational → US1 → Polish

## Implementation Strategy

Feature de história única — MVP e escopo completo coincidem. É bloqueante para as features 011 e 012.
