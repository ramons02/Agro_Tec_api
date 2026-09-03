# Tasks: Visualização Interativa de Talhões e Estações em Mapa

**Input**: Design documents from `/specs/007-mapa-interativo-talhoes/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md; features 002, 005, 006, 011 implementadas

**Tests**: não solicitados explicitamente — omitidos; validação via `quickstart.md`.

**Nota de escopo**: esta feature é majoritariamente frontend (`Agro_Tec_app`, fora deste repositório). As tasks abaixo cobrem apenas o endpoint agregador de dados.

## Phase 1: Setup

*Nenhuma dependência nova.*

## Phase 2: Foundational

- [ ] T001 Definir schema de resposta agregada (`MapaDadosResponse`) em `app/api/v1/endpoints/mapa.py` conforme `data-model.md`

## Phase 3: User Story 1 - Ver propriedades, talhões e estações no mapa (Priority: P1) 🎯 MVP

**Goal**: endpoint único retorna geometrias e estações para renderização.

**Independent Test**: Cenário 1 do `quickstart.md`.

### Implementation for User Story 1

- [ ] T002 [US1] Implementar `GET /api/v1/mapa/dados` em `app/api/v1/endpoints/mapa.py`: agrega propriedades/talhões (via `ST_AsGeoJSON`) e estações do usuário autenticado
- [ ] T003 [US1] Aplicar o mesmo filtro de RBAC da feature 014 (retornar apenas propriedades visíveis ao usuário)

**Checkpoint**: payload consumível pelo frontend disponível.

---

## Phase 4: User Story 2 - Cor do talhão reflete o status de plantio (Priority: P1)

**Goal**: cada talhão no payload carrega seu `status_plantio` atual.

**Independent Test**: Cenário 1 do quickstart, verificando o campo `status_plantio` no payload.

### Implementation for User Story 2

- [ ] T004 [US2] Incluir `status_plantio` (feature 011) no payload de cada talhão retornado por `GET /mapa/dados`

**Checkpoint**: payload completo para colorir o mapa (a renderização em si é do `Agro_Tec_app`).

---

## Phase 5: User Story 3 - Ver detalhes ao clicar (Priority: P2)

**Goal**: payload inclui últimas medições por estação para o popup.

**Independent Test**: Cenário 1 do quickstart, verificando `ultima_medicao` no payload de cada estação.

### Implementation for User Story 3

- [ ] T005 [US3] Incluir a última `MedicaoClima` (feature 002/008) de cada estação no payload de `GET /mapa/dados`

**Checkpoint**: payload completo — nenhuma implementação de UI resta neste repositório.

---

## Phase Final: Polish

- [ ] T006 [P] Escrever teste de contrato em `tests/contract/test_mapa_dados.py` cobrindo escopo por RBAC
- [ ] T007 Rodar os 2 cenários de `quickstart.md`

## Dependencies & Execution Order

Foundational → US1 → US2/US3 (extensões do mesmo endpoint, podem ser feitas juntas) → Polish

## Implementation Strategy

MVP = US1 (geometrias básicas). US2 e US3 são campos adicionais no mesmo payload — de baixo custo para incluir junto com o MVP.
