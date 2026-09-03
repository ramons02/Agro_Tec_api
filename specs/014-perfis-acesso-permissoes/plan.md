# Implementation Plan: Perfis de Acesso e Permissões

**Branch**: `014-perfis-acesso-permissoes` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-perfis-acesso-permissoes/spec.md`

## Summary

Dependência de autorização (FastAPI `Depends`) aplicada a toda rota de propriedades/talhões, verificando papel (`PRODUTOR_RURAL`/`AGRONOMO`/`GESTOR_TECNOLOGIA`) e, para `AGRONOMO`, vínculo aceito com a propriedade; toda ação de escrita bloqueada retorna 403 explícito. Inclui o fluxo de convite/aceite de vínculo agrônomo-propriedade.

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI (dependências de autorização), Pydantic v2, SQLAlchemy Async

**Storage**: PostgreSQL (nova tabela `vinculos_agronomo_propriedade`; `propriedades.proprietario_id` já modelado na feature 005)

**Testing**: pytest + pytest-asyncio, matriz de testes de contrato cobrindo as 3×N combinações de papel × ação × dono/vínculo

**Target Platform**: Linux server (container)

**Project Type**: web-service (backend API)

**Performance Goals**: verificação de permissão não deve adicionar mais que alguns milissegundos ao tempo de resposta já orçado em RNF002

**Constraints**: erro de permissão sempre 403 explícito, nunca 404 disfarçado (FR-006); vínculo nunca unilateral (FR-005)

**Scale/Scope**: aplica-se a toda rota de escrita/leitura de propriedades e talhões do sistema

## Constitution Check

| Princípio | Avaliação |
|---|---|
| I. Assíncrono e Tipado | PASS — dependência de autorização assíncrona (consulta ao vínculo) |
| II. Custo Zero em Integrações Externas | N/A |
| III. Tempo Real sem Cache Obsoleto | N/A |
| IV. Geoprocessamento Correto e Verificável | N/A |
| V. Segurança JWT e Segredos Fora do Git | PASS — é a extensão direta do princípio de segurança da feature 001, adicionando autorização por papel além de autenticação |

Nenhuma violação. Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/014-perfis-acesso-permissoes/
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
├── core/
│   └── security.py               # extensão: dependências require_dono_ou_gestor, require_leitura_vinculada
├── api/v1/endpoints/
│   ├── propriedades.py            # aplica as dependências de autorização (extensão da feature 005)
│   ├── talhoes.py                 # idem
│   └── vinculos.py                 # POST /vinculos (convite), POST /vinculos/{id}/aceitar
└── db/models/
    └── vinculo_agronomo_propriedade.py

tests/
└── contract/
    └── test_autorizacao_propriedades_talhoes.py
```

**Structure Decision**: autorização implementada como dependências FastAPI reutilizáveis, aplicadas nas rotas já existentes das features 005/006/007/011, em vez de duplicar checagem em cada endpoint manualmente.

## Complexity Tracking

*Sem violações de constituição a justificar.*
