# Implementation Plan: Dashboard de Status de Plantio por Talhão

**Branch**: `011-dashboard-status-plantio` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-dashboard-status-plantio/spec.md`

## Summary

A renderização do painel é responsabilidade do `Agro_Tec_app` (fora de escopo aqui). Este repositório fornece: (1) a classificação de status Verde/Amarelo/Vermelho por talhão, derivada do armazenamento diário (feature 010) conforme RN004-RN006, e (2) um endpoint de listagem com filtro por propriedade e por status, paginado.

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI, Pydantic v2, SQLAlchemy Async

**Storage**: leitura de `balanco_hidrico_diario` (feature 010) e `talhoes` (feature 005); status pode ser calculado on-the-fly ou persistido em coluna derivada (ver research.md)

**Testing**: pytest com testes de tabela para os limiares de status (0,30/0,60/0,90/0,95 × CAD) e teste de contrato do endpoint com filtros

**Target Platform**: Linux server (container)

**Project Type**: web-service (backend API) — UI do painel pertence ao `Agro_Tec_app`

**Performance Goals**: listagem com filtros responde em menos de 2 segundos (RNF002)

**Constraints**: paginação obrigatória acima de 50 itens, 20 por página como padrão (RNF017)

**Scale/Scope**: todos os talhões visíveis ao usuário autenticado (RBAC da feature 014)

## Constitution Check

| Princípio | Avaliação |
|---|---|
| I. Assíncrono e Tipado | PASS |
| II. Custo Zero em Integrações Externas | N/A |
| III. Tempo Real sem Cache Obsoleto | N/A — status de plantio é diário, não sujeito à regra de 30 min |
| IV. Geoprocessamento Correto e Verificável | N/A |
| V. Segurança JWT e Segredos Fora do Git | PASS — endpoint autenticado, escopo por RBAC |

Nenhuma violação. Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/011-dashboard-status-plantio/
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
│   └── calculos/
│       └── status_plantio.py     # classificar_status(armazenamento_mm, cad_mm, chuva_prevista_mm) -> Enum — função pura
└── api/v1/endpoints/
    └── dashboard.py                # GET /dashboard/plantio?propriedade_id=&status=&page=

tests/
└── unit/
    └── test_calculos_status_plantio.py
```

**Structure Decision**: classificação como função pura em `core/calculos/`, mesmo padrão de 009/010; endpoint de listagem fino que aplica filtros e paginação sobre o resultado.

## Complexity Tracking

*Sem violações de constituição a justificar.*
