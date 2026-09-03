# Tasks: Recomendacao Acionavel de "Proximo Passo" por Talhao

**Input**: Design documents from `/specs/012-recomendacao-proximo-passo/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md; features 009 (pulverizacao) e 011 (status plantio) implementadas

**Tests**: nao solicitados explicitamente — verificacao de tabela incluida como parte central da impl (FR-008 exige auditabilidade).

## Phase 1: Setup

*Sem dependencia nova.*

## Phase 2: Foundational

- [ ] T001 Definir schema `Recomendacao` (texto, prioridade enum, aviso) em `app/core/calculos/recomendacao.py`

## Phase 3: User Story 1 - Ver recomendacao unica (Priority: P1) 🎯 MVP

**Goal**: combina status plantio + pulverizacao numa frase.

**Independent Test**: Cenario 1 do `quickstart.md`.

### Implementation for User Story 1

- [ ] T002 [US1] Implementar `gerar_recomendacao(status_plantio, status_pulverizacao, tendencia_umidade) -> Recomendacao` funcao pura, portada de `recomendacao.ts`
- [ ] T003 [US1] Implementar endpoint `GET /api/v1/talhoes/{id}/recomendacao` (contrato `contracts/recomendacao.md`): busca status plantio (011) + pulverizacao (009), chama `gerar_recomendacao`

**Checkpoint**: recomendacao funcional pro caso basico.

---

## Phase 4: User Story 2 - Priorizacao clara (Priority: P1)

**Goal**: prioridade Alta/Media/Baixa, sempre cor+texto.

**Independent Test**: Cenarios 1-2 do `quickstart.md` (RN011-013).

### Implementation for User Story 2

- [ ] T004 [US2] Impl regra de prioridade em `gerar_recomendacao`: Vermelho→ALTA (sempre); Amarelo ou pulverizacao bloqueada→MEDIA; resto→BAIXA
- [ ] T005 [P] [US2] Teste de tabela cobrindo combinacoes de prioridade em `tests/unit/test_calculos_recomendacao.py`

**Checkpoint**: prioridade correta em toda combinacao.

---

## Phase 5: User Story 3 - Tendencia de umidade em status Amarelo (Priority: P2)

**Goal**: texto reflete melhora/piora quando Amarelo.

**Independent Test**: Cenario 3 do `quickstart.md` (RN019, 1.5 p.p./3 dias).

### Implementation for User Story 3

- [ ] T006 [US3] Implementar `calcular_tendencia_umidade(armazenamento_hoje, armazenamento_3dias_atras, cad_mm) -> Enum` (subindo/caindo/estavel, limiar 1.5 p.p.) em `app/core/calculos/recomendacao.py`
- [ ] T007 [US3] Usar tendencia no texto de `gerar_recomendacao` so quando status = Amarelo

**Checkpoint**: recomendacao completa, todas regras da spec cobertas.

---

## Phase Final: Polish

- [ ] T008 Garantir aviso fixo ("sugestao automatica...") sempre presente na resposta (FR-007)
- [ ] T009 Rodar Cenarios 1-3 do `quickstart.md`

## Dependencies & Execution Order

Foundational → US1 → US2 → US3 → Polish

## Implementation Strategy

MVP = US1+US2 (recomendacao com prioridade correta). US3 (tendencia) refina texto no caso Amarelo, incremento seguro.
