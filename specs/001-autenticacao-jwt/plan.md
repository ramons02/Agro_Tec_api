# Implementation Plan: Autenticação e Segurança de Usuário via Token JWT

**Branch**: `001-autenticacao-jwt` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-autenticacao-jwt/spec.md`

## Summary

Endpoint de login que valida usuário e senha e emite um token JWT de 24 horas; middleware/dependência de autenticação que exige `Authorization: Bearer <token>` em toda rota protegida e rejeita com HTTP 401 quando o token estiver ausente, inválido ou expirado. Segredos de assinatura carregados exclusivamente via `.env`.

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI, Pydantic v2, python-jose (JWT), passlib[bcrypt] (hash de senha), SQLAlchemy Async + asyncpg

**Storage**: PostgreSQL (tabela `usuarios`; sem PostGIS necessário nesta feature)

**Testing**: pytest + pytest-asyncio + httpx.AsyncClient (testes de contrato do endpoint de login e da dependência de autenticação)

**Target Platform**: Linux server (container)

**Project Type**: web-service (backend API)

**Performance Goals**: login e validação de token respondem em menos de 2 segundos (RNF002)

**Constraints**: segredos de assinatura JWT nunca em texto no repositório (Constituição, Princípio V); token com validade fixa de 24 horas (RNF009)

**Scale/Scope**: autenticação de até milhares de usuários (produtores, agrônomos, gestores) no Pará — sem exigência de escala massiva no MVP

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação |
|---|---|
| I. Assíncrono e Tipado | PASS — endpoint e verificação de hash declarados `async def`; validação via Pydantic v2 Schemas (`LoginRequest`, `TokenResponse`) |
| II. Custo Zero em Integrações Externas | N/A — feature não depende de API externa |
| III. Tempo Real sem Cache Obsoleto | N/A — não é rota de dado climático |
| IV. Geoprocessamento Correto e Verificável | N/A — feature não envolve geometria |
| V. Segurança JWT e Segredos Fora do Git | PASS — é o objeto desta feature; chave de assinatura via `core/config.py` (`BaseSettings`) lendo `.env` |

Nenhuma violação. Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/001-autenticacao-jwt/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
app/
├── api/
│   └── v1/
│       ├── endpoints/
│       │   └── auth.py          # POST /api/v1/auth/login
│       └── router.py
├── core/
│   ├── config.py                 # BaseSettings: JWT_SECRET, JWT_EXPIRATION_HOURS
│   └── security.py               # criação/validação de token, dependência get_current_user
├── db/
│   └── models/
│       └── usuario.py            # modelo ORM Usuario (id, email, senha_hash, papel)
└── main.py

tests/
├── contract/
│   └── test_auth_login.py
└── unit/
    └── test_security.py
```

**Structure Decision**: projeto único (Option 1), seguindo a estrutura de pastas definida na Convenção de Desenvolvimento (`app/api/v1/endpoints`, `app/core`, `app/db/models`). Não há frontend nesta feature — o consumo do endpoint é feito pelo repositório `Agro_Tec_app` (fora de escopo aqui).

## Complexity Tracking

*Sem violações de constituição a justificar.*
