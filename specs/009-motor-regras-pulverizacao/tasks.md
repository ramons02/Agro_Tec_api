# Tasks: Motor de Regras e Alerta de Janela Segura para Pulverização

**Input**: Design documents from `/specs/009-motor-regras-pulverizacao/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md; feature 006/008 fornecem a leitura de vento

**Tests**: não solicitados explicitamente na spec, mas o Success Criteria SC-001 exige verificação por teste automatizado de fronteiras — incluído como tarefa de implementação central, não como fase de TDD separada.

## Phase 1: Setup

*Nenhuma dependência nova — função pura sem I/O.*

## Phase 2: Foundational

- [ ] T001 Definir enum `ClassificacaoPulverizacao` (`FAVORAVEL`, `BLOQUEIO_VENTO_FORTE`, `BLOQUEIO_INVERSAO_TERMICA`) em `app/core/calculos/pulverizacao.py`

## Phase 3: User Story 1 - Classificar a janela de pulverização (Priority: P1) 🎯 MVP

**Goal**: classificação determinística a partir de vento/rajada.

**Independent Test**: tabela de casos do `quickstart.md` (incluindo fronteiras 3, 10, 15 km/h).

### Implementation for User Story 1

- [ ] T002 [US1] Implementar `classificar_pulverizacao(vento_kmh, rajada_kmh) -> ClassificacaoPulverizacao` em `app/core/calculos/pulverizacao.py`, portando `regrasPulverizacao.ts`: inversão térmica (vento < 3, checada primeiro e isoladamente) → vento forte (vento > 10 ou rajada > 15) → favorável (caso contrário)
- [ ] T003 [P] [US1] Escrever teste de tabela cobrindo todos os casos do `quickstart.md`, incluindo as fronteiras exatas, em `tests/unit/test_calculos_pulverizacao.py`

**Checkpoint**: função de classificação validada e correta.

---

## Phase 4: User Story 2 - Exibir alerta visual destacado (Priority: P2)

**Goal**: endpoint expõe a classificação para consumo pela UI (renderização em si é do `Agro_Tec_app`).

**Independent Test**: consultar o endpoint e confirmar o campo `classificacao` presente.

### Implementation for User Story 2

- [ ] T004 [US2] Implementar endpoint `GET /api/v1/talhoes/{id}/pulverizacao` (contrato em `contracts/pulverizacao.md`): resolve estação mais próxima (feature 006), obtém leitura via feature 008, aplica `classificar_pulverizacao`
- [ ] T005 [US2] Tratar ausência de leitura de vento disponível sem apresentar uma classificação como se fosse válida (retornar indicação explícita de dado indisponível)

**Checkpoint**: endpoint completo, pronto para consumo pelo frontend e pela feature 012.

---

## Phase Final: Polish

- [ ] T006 Rodar os 4 cenários de `quickstart.md`

## Dependencies & Execution Order

Foundational → US1 → US2 → Polish

## Implementation Strategy

MVP = US1 (função de classificação correta e testada). US2 (endpoint) é o empacotamento necessário para uso real, mas a lógica de negócio já está completa e correta ao final de US1.
