# Tasks: Parametrização Automática de Solo via SoilGrids

**Input**: Design documents from `/specs/004-solo-soilgrids/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md; feature 005 (talhões) fornece o gatilho de cadastro

**Tests**: não solicitados explicitamente — omitidos; validação via `quickstart.md`.

## Phase 1: Setup

- [ ] T001 [P] Adicionar dependência `httpx` (já presente desde a feature 002/003) — nenhuma nova lib necessária

## Phase 2: Foundational

- [ ] T002 Adicionar colunas de solo (`tipo_solo`, `fracao_argila_pct`, `fracao_areia_pct`, `fracao_silte_pct`, `materia_organica_pct`, `capacidade_agua_disponivel_mm`) ao modelo `Talhao` (`app/db/models/talhao.py`) e migração Alembic correspondente

**Checkpoint**: schema de talhão pronto para receber os dados de solo.

---

## Phase 3: User Story 1 - Classificação automática de solo no cadastro (Priority: P1) 🎯 MVP

**Goal**: coordenada do talhão gera classificação de solo automaticamente.

**Independent Test**: informar coordenada válida e confirmar `tipo_solo` classificado (Cenário 1 do quickstart).

### Implementation for User Story 1

- [ ] T003 [P] [US1] Implementar `app/services/soilgrids_service.py`: cliente `httpx` assíncrono para a API SoilGrids, extraindo argila/areia/silte/matéria orgânica
- [ ] T004 [US1] Implementar `classificar_textura(argila, areia, silte)` em `app/core/calculos/solo.py` (função pura, triângulo textural padrão)
- [ ] T005 [US1] Integrar `soilgrids_service` + `classificar_textura` ao fluxo de criação de talhão (feature 005): ao criar/atualizar geometria, disparar a parametrização

**Checkpoint**: talhão novo recebe `tipo_solo` automaticamente.

---

## Phase 4: User Story 2 - Cálculo da Capacidade de Água Disponível (Priority: P1)

**Goal**: CAD calculada e persistida a partir do perfil de solo.

**Independent Test**: com frações de solo já obtidas, confirmar CAD calculada (Cenário do quickstart, fórmula de `calculos-geo-metero.md` §4A).

### Implementation for User Story 2

- [ ] T006 [P] [US2] Implementar `calcular_cad(cc, pmp, densidade_solo, profundidade_mm)` em `app/core/calculos/solo.py` (função pura, fórmula RN020)
- [ ] T007 [US2] Usar profundidade padrão de 300mm (ver research.md) quando não informada
- [ ] T008 [US2] Persistir `capacidade_agua_disponivel_mm` no talhão junto com a classificação de textura (mesma transação do T005)

**Checkpoint**: talhão criado já possui tipo de solo e CAD, prontos para o Balanço Hídrico (feature 010).

---

## Phase Final: Polish

- [ ] T009 [P] Tratar ausência de cobertura do SoilGrids: talhão salvo com campos de solo nulos, sem bloquear o cadastro (FR-006)
- [ ] T010 [P] Escrever testes unitários de `classificar_textura` e `calcular_cad` com casos de fronteira em `tests/unit/test_calculos_solo.py`
- [ ] T011 Rodar os 2 cenários de `quickstart.md`

## Dependencies & Execution Order

Setup → Foundational → US1 → US2 (US2 depende dos dados obtidos por US1) → Polish

## Parallel Example

```bash
Task: "T003 Implementar soilgrids_service.py"
Task: "T004 Implementar classificar_textura"
```

## Implementation Strategy

MVP = US1 + US2 juntas — a classificação de solo sem a CAD não desbloqueia o Balanço Hídrico (feature 010), então ambas são necessárias para o valor completo desta feature.
