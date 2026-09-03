# Implementation Plan: Cadastro de Conta e Recuperação de Senha

**Branch**: `013-cadastro-conta-recuperacao-senha` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-cadastro-conta-recuperacao-senha/spec.md`

## Summary

Endpoints `/api/v1/auth/registro` (nome, email, senha, papel, com hash bcrypt e unicidade de email) e `/api/v1/auth/recuperar-senha` (envio de link com token de expiração de 1h, resposta uniforme para email existente/inexistente).

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI, Pydantic v2, passlib[bcrypt] (mesma escolha da feature 001), biblioteca de envio de email (ex.: `aiosmtplib` ou provedor transacional gratuito de baixo volume)

**Storage**: PostgreSQL (`usuarios`, `tokens_recuperacao_senha`)

**Testing**: pytest + pytest-asyncio; teste de unicidade de email, expiração de token, uso único de token

**Target Platform**: Linux server (container)

**Project Type**: web-service (backend API)

**Performance Goals**: cadastro e solicitação de recuperação respondem em menos de 2 segundos (RNF002), excluindo o tempo de entrega efetiva do email (assíncrono)

**Constraints**: senha sempre com hash (Princípio V); resposta uniforme para não revelar existência de email (FR-006)

**Scale/Scope**: volume de cadastro esperado é baixo/moderado (produtores de uma região) — sem exigência de escala massiva

## Constitution Check

| Princípio | Avaliação |
|---|---|
| I. Assíncrono e Tipado | PASS — endpoints assíncronos, hash de senha em thread pool (bcrypt é CPU-bound, ver research.md) |
| II. Custo Zero em Integrações Externas | PASS — envio de email deve usar um provedor com tier gratuito suficiente para o volume esperado, sem custo obrigatório |
| III. Tempo Real sem Cache Obsoleto | N/A |
| IV. Geoprocessamento Correto e Verificável | N/A |
| V. Segurança JWT e Segredos Fora do Git | PASS — reaproveita a mesma infraestrutura de segredos da feature 001 (`.env`) |

Nenhuma violação. Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/013-cadastro-conta-recuperacao-senha/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md
```

### Source Code (repository root)

```text
app/
├── api/v1/endpoints/
│   └── auth.py                   # extensão: POST /auth/registro, POST /auth/recuperar-senha, POST /auth/redefinir-senha
├── services/
│   └── email_service.py           # envio assíncrono de email transacional
└── db/models/
    ├── usuario.py                 # extensão: criação (feature 001 já define o schema)
    └── token_recuperacao_senha.py

tests/
└── contract/
    └── test_auth_registro_recuperacao.py
```

**Structure Decision**: extensão do mesmo módulo `auth.py` da feature 001, já que ambas compartilham a entidade `Usuario` e o mesmo domínio de autenticação.

## Complexity Tracking

*Sem violações de constituição a justificar.*
