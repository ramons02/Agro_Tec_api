# Implementation Plan: Exportação de Relatório de Talhões

**Branch**: `015-exportacao-relatorio-talhoes` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-exportacao-relatorio-talhoes/spec.md`

## Summary

Endpoint que retorna, em CSV com BOM UTF-8, os talhões que respeitam os mesmos filtros (propriedade, status) do Dashboard de Plantio (feature 011), sem paginação (todos os itens filtrados de uma vez). O botão de exportar e o download em si são responsabilidade do `Agro_Tec_app`; este repositório entrega apenas o dado já formatado.

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI (`StreamingResponse` ou `Response` com `text/csv`), módulo `csv` da stdlib

**Storage**: leitura de `talhoes`/`balanco_hidrico_diario` (mesma fonte da feature 011, sem novo dado)

**Testing**: pytest, incluindo teste de encoding (BOM UTF-8) e de que o filtro aplicado bate exatamente com o resultado exportado

**Target Platform**: Linux server (container)

**Project Type**: web-service (backend API) — o botão/download em si é UI do `Agro_Tec_app`

**Performance Goals**: exportação de até alguns milhares de talhões em menos de 3 segundos (SC-001 da spec)

**Constraints**: encoding UTF-8 com BOM para compatibilidade com Excel em português (FR-003)

**Scale/Scope**: exporta exatamente o conjunto filtrado, sem limite artificial de paginação

## Constitution Check

| Princípio | Avaliação |
|---|---|
| I. Assíncrono e Tipado | PASS — endpoint assíncrono, reaproveita os mesmos schemas de filtro da feature 011 |
| II. Custo Zero em Integrações Externas | N/A |
| III. Tempo Real sem Cache Obsoleto | N/A — reflete o status diário já calculado (feature 010/011) |
| IV. Geoprocessamento Correto e Verificável | N/A |
| V. Segurança JWT e Segredos Fora do Git | PASS — mesma autenticação e escopo RBAC do Dashboard (feature 011/014) |

Nenhuma violação. Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/015-exportacao-relatorio-talhoes/
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
│   └── dashboard.py               # extensão: GET /dashboard/plantio/exportar.csv (mesmos filtros da feature 011, sem paginação)
└── services/
    └── exportacao_csv_service.py  # monta as linhas CSV + BOM, reaproveitando a consulta filtrada

tests/
└── contract/
    └── test_exportacao_csv.py
```

**Structure Decision**: endpoint dedicado que reaproveita a mesma consulta filtrada da feature 011, sem paginação — decisão explícita para a Assumption deixada em aberto na spec (client-side vs. endpoint): optou-se por endpoint de backend porque a exportação deve refletir *todos* os talhões filtrados, não apenas a página carregada no frontend.

## Complexity Tracking

*Sem violações de constituição a justificar.*
