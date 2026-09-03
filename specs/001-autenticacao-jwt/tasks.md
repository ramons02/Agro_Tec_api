# Tasks: Autenticação e Segurança de Usuário via Token JWT

**Input**: Design documents from `/specs/001-autenticacao-jwt/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: não solicitados explicitamente na spec — omitidos; a validação funcional é feita via `quickstart.md`.

## Phase 1: Setup

**Purpose**: esta é a primeira feature implementada do projeto — inclui o bootstrap do backend inteiro, não só desta feature.

- [X] T001 Criar esqueleto do projeto FastAPI (`app/main.py`, `app/api/v1/router.py`) conforme a estrutura de pastas da Convenção de Desenvolvimento
- [X] T002 Configurar `pyproject.toml`/`requirements.txt` com FastAPI, Pydantic v2, SQLAlchemy Async, asyncpg, python-jose[cryptography], passlib[bcrypt], pytest, pytest-asyncio, httpx
- [X] T003 [P] Configurar lint/format (ruff ou flake8 + black) conforme PEP 8 (Princípio I da Constituição)
- [X] T004 [P] Criar `app/core/config.py` com `BaseSettings` lendo `.env` (incluindo `JWT_SECRET`, `JWT_EXPIRATION_HOURS=24`, `DATABASE_URL`)

## Phase 2: Foundational

**Purpose**: infraestrutura bloqueante para toda a aplicação, não só para autenticação.

- [X] T005 Criar `app/db/session.py` com engine assíncrono SQLAlchemy + PostgreSQL
- [X] T006 Criar modelo `Usuario` em `app/db/models/usuario.py` (id, email único, senha_hash, papel enum, criado_em) conforme `data-model.md`
- [X] T007 [P] Criar migração inicial (Alembic) para a tabela `usuarios`
- [X] T008 [P] Implementar envelope de resposta padrão (`{"status": "sucesso"|"erro", ...}`) em `app/core/response.py`, conforme Convenção Técnica §5
- [X] T009 [P] Implementar handler global de exceções FastAPI que traduz erros para o envelope padrão

**Checkpoint**: base pronta — as duas User Stories podem começar.

---

## Phase 3: User Story 1 - Login com credenciais válidas (Priority: P1) 🎯 MVP

**Goal**: usuário cadastrado envia usuário/senha e recebe um token JWT válido por 24h.

**Independent Test**: enviar credenciais válidas a `/api/v1/auth/login` e confirmar token e expiração na resposta (Cenário 1 do `quickstart.md`).

### Implementation for User Story 1

- [X] T010 [P] [US1] Criar schemas Pydantic `LoginRequest`/`TokenResponse` em `app/api/v1/endpoints/auth.py`
- [X] T011 [US1] Implementar `app/core/security.py`: `criar_token(usuario)`, `verificar_senha(senha, hash)` (bcrypt via executor, ver research.md)
- [X] T012 [US1] Implementar endpoint `POST /api/v1/auth/login` em `app/api/v1/endpoints/auth.py` (contrato em `contracts/auth-login.md`)
- [X] T013 [US1] Registrar rota de auth em `app/api/v1/router.py`
- [X] T014 [US1] Tratar credenciais inválidas com HTTP 401 e mensagem genérica (sem revelar se o email existe)

**Checkpoint**: login funcional e testável isoladamente (Cenários 1 e 2 do quickstart).

---

## Phase 4: User Story 2 - Bloqueio de acesso não autenticado (Priority: P1)

**Goal**: toda rota protegida rejeita requisição sem token válido.

**Independent Test**: chamar uma rota protegida sem token, com token expirado e com token malformado (Cenários 3 e 4 do `quickstart.md`).

### Implementation for User Story 2

- [X] T015 [US2] Implementar dependência `get_current_user` em `app/core/security.py` (decodifica e valida JWT, levanta 401 se ausente/inválido/expirado)
- [X] T016 [US2] Aplicar `Depends(get_current_user)` como padrão de proteção documentado para futuras rotas (usado a partir da feature 005 em diante)
- [X] T017 [US2] Criar uma rota de teste mínima protegida (ex.: `GET /api/v1/auth/me`) para validar a dependência isoladamente

**Checkpoint**: autenticação completa — login emite token, rotas protegidas o exigem.

---

## Phase Final: Polish

- [X] T018 [P] Escrever testes unitários de `security.py` (hash, criação e validação de token, incluindo expiração) em `tests/unit/test_security.py`
- [X] T019 [P] Escrever teste de contrato de `/auth/login` em `tests/contract/test_auth_login.py`
- [X] T020 Rodar todos os cenários de `quickstart.md` manualmente para validação final
- [X] T021 Confirmar que nenhum segredo (`JWT_SECRET`) está commitado — placeholder movido para `Agro_Tec_infra/api.env.example` (separação de repositórios definida pelo usuário em 2026-09-03)

## Dependencies & Execution Order

- Setup (Fase 1) → Foundational (Fase 2) → US1 (Fase 3) → US2 (Fase 4) → Polish
- US2 depende de US1 existir (precisa de um endpoint de login para gerar o token a testar), mas a lógica de rejeição (`get_current_user`) é implementável em paralelo com T010-T014 já que não depende do endpoint de login em si.

## Parallel Example

```bash
# Após Foundational, em paralelo:
Task: "T010 Criar schemas Pydantic LoginRequest/TokenResponse"
Task: "T015 Implementar dependência get_current_user"
```

## Implementation Strategy

MVP = US1 (login funcional) + US2 (bloqueio de rota) — as duas juntas já entregam o valor completo desta feature; não há incremento parcial menor que faça sentido isoladamente.
