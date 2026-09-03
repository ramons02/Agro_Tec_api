# Tasks: Cadastro de Conta e Recuperacao de Senha

**Input**: Design documents from `/specs/013-cadastro-conta-recuperacao-senha/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md; feature 001 (auth/Usuario ja modelado)

**Tests**: nao solicitados explicitamente — omitidos; validacao via `quickstart.md`.

## Phase 1: Setup

- [ ] T001 Add dependencia envio email (`aiosmtplib`) ao projeto

## Phase 2: Foundational

- [ ] T002 Criar modelo `TokenRecuperacaoSenha` (usuario_id, token, expira_em, usado_em) + migracao
- [ ] T003 [P] Implementar `app/services/email_service.py`: interface `EmailService` + impl concreta (provedor gratuito)

## Phase 3: User Story 1 - Criar conta nova (Priority: P1) 🎯 MVP

**Goal**: cadastro com nome/email/senha/papel, email unico, senha hash.

**Independent Test**: Cenarios 1-2 do `quickstart.md`.

### Implementation for User Story 1

- [ ] T004 [US1] Add campo `nome` ao modelo `Usuario` (feature 001) se ausente + migracao
- [ ] T005 [US1] Implementar `POST /api/v1/auth/registro` em `app/api/v1/endpoints/auth.py` (contrato `contracts/auth-registro-recuperacao.md`)
- [ ] T006 [US1] Validar unicidade email → 409; validar senha min 8 chars → 422; hash bcrypt via executor (nao bloqueante, ver research.md)

**Checkpoint**: registro funcional, login (feature 001) aceita conta recem-criada.

---

## Phase 4: User Story 2 - Recuperar senha esquecida (Priority: P1)

**Goal**: link com token 1h, uso unico, resposta uniforme.

**Independent Test**: Cenarios 3-4 do `quickstart.md`.

### Implementation for User Story 2

- [ ] T007 [US2] Implementar `POST /api/v1/auth/recuperar-senha`: gera token opaco, expira_em=+1h, envia email via `EmailService`, resposta identica exista ou nao o email (FR-006)
- [ ] T008 [US2] Implementar `POST /api/v1/auth/redefinir-senha`: valida token nao expirado/nao usado, atualiza `senha_hash`, marca `usado_em`
- [ ] T009 [US2] Rejeitar token expirado/ja usado com HTTP 400 e mensagem clara (nunca erro generico)

**Checkpoint**: fluxo completo de recuperacao funcional.

---

## Phase Final: Polish

- [ ] T010 [P] Teste de contrato cobrindo unicidade, expiracao, uso unico em `tests/contract/test_auth_registro_recuperacao.py`
- [ ] T011 Rodar Cenarios 1-4 do `quickstart.md`

## Dependencies & Execution Order

Setup → Foundational → US1 → US2 → Polish

## Implementation Strategy

MVP = US1 (cadastro). US2 (recuperacao) critico antes de producao real — sem ela usuario perde conta pra sempre se esquecer senha.
