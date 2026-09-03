# Tasks: Perfis de Acesso e Permissoes

**Input**: Design documents from `/specs/014-perfis-acesso-permissoes/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md; features 001, 005 implementadas

**Tests**: nao solicitados explicitamente — omitidos; validacao via `quickstart.md`.

## Phase 1: Setup

*Sem dependencia nova.*

## Phase 2: Foundational

- [ ] T001 Criar modelo `VinculoAgronomoPropriedade` (agronomo_id, propriedade_id, estado enum CONVIDADO/ACEITO/REVOGADO) + migracao

## Phase 3: User Story 1 - Produtor gerencia so proprias propriedades (Priority: P1) 🎯 MVP

**Goal**: PRODUTOR_RURAL edita/exclui so oque e dono.

**Independent Test**: Cenario 1 do `quickstart.md`.

### Implementation for User Story 1

- [ ] T002 [US1] Implementar dependencia `require_dono_ou_gestor(propriedade_id, usuario)` em `app/core/security.py`: PASS se GESTOR_TECNOLOGIA ou dono; senao 403
- [ ] T003 [US1] Aplicar `require_dono_ou_gestor` nas rotas de escrita de propriedades/talhoes (feature 005: `PUT`/`DELETE`)

**Checkpoint**: isolamento entre produtores garantido.

---

## Phase 4: User Story 2 - Agronomo acesso leitura vinculada (Priority: P1)

**Goal**: agronomo so ve/le propriedades vinculadas e aceitas.

**Independent Test**: Cenario 2 do `quickstart.md`.

### Implementation for User Story 2

- [ ] T004 [US2] Implementar filtro `propriedades_visiveis(usuario)` em `app/core/security.py`: GESTOR→todas; PRODUTOR→proprias; AGRONOMO→vinculos com estado=ACEITO
- [ ] T005 [US2] Aplicar filtro em `GET /propriedades`, `GET /talhoes`, `GET /mapa/dados` (007), `GET /dashboard/plantio` (011)
- [ ] T006 [US2] Bloquear qualquer escrita de AGRONOMO com 403 (mesmo se vinculado)

**Checkpoint**: agronomo restrito a leitura vinculada em toda rota relevante.

---

## Phase 5: User Story 3 - Vinculo exige aceite (Priority: P2)

**Goal**: convite so vale apos aceite explicito.

**Independent Test**: Cenario 3 do `quickstart.md`.

### Implementation for User Story 3

- [ ] T007 [US3] Implementar `POST /api/v1/propriedades/{id}/vinculos` (convite, dono/gestor only) — contrato `contracts/vinculos.md`
- [ ] T008 [US3] Implementar `POST /api/v1/vinculos/{id}/aceitar` (proprio agronomo): muda estado→ACEITO

**Checkpoint**: fluxo convite→aceite completo.

---

## Phase 6: User Story 4 - Gestor tecnologia acesso total (Priority: P2)

**Goal**: GESTOR_TECNOLOGIA le/escreve tudo.

**Independent Test**: Cenario 4 do `quickstart.md`.

### Implementation for User Story 4

- [ ] T009 [US4] Confirmar bypass de GESTOR_TECNOLOGIA em T002 e T004 ja cobre este caso — teste dedicado apenas

**Checkpoint**: todas 4 historias completas.

---

## Phase Final: Polish

- [ ] T010 Garantir 403 explicito (nunca 404) em toda rota bloqueada por permissao (FR-006)
- [ ] T011 [P] Matriz de testes de contrato papel×acao×dono/vinculo em `tests/contract/test_autorizacao_propriedades_talhoes.py`
- [ ] T012 Rodar Cenarios 1-4 do `quickstart.md`

## Dependencies & Execution Order

Foundational → US1 → US2 → US3 → US4 → Polish (US4 trivial apos US1/US2)

## Implementation Strategy

MVP = US1+US2 (isolamento basico produtor/agronomo). US3 (convite) e US4 (gestor) fecham o modelo completo.
