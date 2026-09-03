# Tasks: Dashboard de Status de Plantio por Talhão

**Input**: Design documents from `/specs/011-dashboard-status-plantio/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md; feature 010 (balanco hidrico) implementada

**Tests**: nao solicitados explicitamente — omitidos; validacao via `quickstart.md`.

**Nota de escopo**: renderizacao do painel = `Agro_Tec_app`. Aqui so classificacao + endpoint de listagem.

## Phase 1: Setup

*Sem dependencia nova.*

## Phase 2: Foundational

- [ ] T001 Add coluna `status_plantio` (enum VERDE/AMARELO/VERMELHO) em `BalancoHidricoDiario` (feature 010) + migracao Alembic

**Checkpoint**: schema pronto pra carregar status junto do calculo diario.

---

## Phase 3: User Story 1 - Ver status de plantio de todos os talhoes (Priority: P1) 🎯 MVP

**Goal**: cada talhao classificado Verde/Amarelo/Vermelho.

**Independent Test**: 3 acceptance scenarios da spec (Verde/Amarelo/Vermelho).

### Implementation for User Story 1

- [ ] T002 [US1] Implementar `classificar_status(armazenamento_mm, cad_mm, chuva_prevista_mm) -> Enum` em `app/core/calculos/status_plantio.py` (funcao pura, RN004-RN006, fallback conservador ja validado)
- [ ] T003 [P] [US1] Teste de tabela cobrindo os limiares (30/60/90/95% CAD) em `tests/unit/test_calculos_status_plantio.py`
- [ ] T004 [US1] Chamar `classificar_status` dentro do job diario da feature 010 (T005 de 010-balanco-hidrico-solo/tasks.md), persistindo `status_plantio` junto

**Checkpoint**: status calculado e persistido diariamente.

---

## Phase 4: User Story 2 - Filtrar o painel (Priority: P2)

**Goal**: listagem filtravel por propriedade e status, paginada.

**Independent Test**: Cenarios 2 e 3 do `quickstart.md`.

### Implementation for User Story 2

- [ ] T005 [US2] Implementar `GET /api/v1/dashboard/plantio?propriedade_id=&status=&page=&page_size=` em `app/api/v1/endpoints/dashboard.py` (contrato em `contracts/dashboard-plantio.md`)
- [ ] T006 [US2] Aplicar paginacao (20/pagina default, RNF017) e escopo RBAC (feature 014)

**Checkpoint**: painel de dados completo, pronto pro `Agro_Tec_app` consumir.

---

## Phase Final: Polish

- [ ] T007 Escrever teste de contrato do endpoint com filtros em `tests/contract/`
- [ ] T008 Rodar Cenarios 1-3 do `quickstart.md`

## Dependencies & Execution Order

Foundational → US1 → US2 → Polish

## Implementation Strategy

MVP = US1 (classificacao correta). US2 (filtro/paginacao) e a UX de consumo — sem ela dashboard ainda funciona, so sem filtro.
