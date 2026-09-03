# Implementation Plan: Recomendação Acionável de "Próximo Passo" por Talhão

**Branch**: `012-recomendacao-proximo-passo` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-recomendacao-proximo-passo/spec.md`

## Summary

Função pura que combina status de plantio (feature 011) e status de pulverização (feature 009) num texto curto + prioridade (Alta/Média/Baixa), considerando tendência de umidade dos últimos 3 dias quando o status é Amarelo (RN019, limiar de 1,5 p.p.), exposta via endpoint de detalhe do talhão.

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI, Pydantic v2 (sem dependência externa — lógica pura)

**Storage**: leitura de `balanco_hidrico_diario` (últimos 3 dias, feature 010) e do status de pulverização calculado (feature 009)

**Testing**: pytest com testes de tabela cobrindo as combinações de prioridade (RN011-RN013) e a classificação de tendência (RN019)

**Target Platform**: Linux server (container)

**Project Type**: web-service (backend API)

**Performance Goals**: geração da recomendação instantânea (função pura); tempo de resposta do endpoint dominado pelas consultas que ela combina

**Constraints**: lógica isolável e auditável, sem efeitos colaterais (FR-008)

**Scale/Scope**: uma recomendação por consulta de detalhe de talhão

## Constitution Check

| Princípio | Avaliação |
|---|---|
| I. Assíncrono e Tipado | PASS — endpoint assíncrono; função de recomendação pura e tipada |
| II. Custo Zero em Integrações Externas | N/A |
| III. Tempo Real sem Cache Obsoleto | PASS — usa o status de pulverização mais recente (feature 009/008) |
| IV. Geoprocessamento Correto e Verificável | N/A |
| V. Segurança JWT e Segredos Fora do Git | PASS — rota autenticada |

Nenhuma violação. Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/012-recomendacao-proximo-passo/
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
│       └── recomendacao.py       # gerar_recomendacao(status_plantio, status_pulverizacao, tendencia_umidade) -> Recomendacao — função pura
└── api/v1/endpoints/
    └── talhoes.py                 # GET /talhoes/{id}/recomendacao (extensão)

tests/
└── unit/
    └── test_calculos_recomendacao.py
```

**Structure Decision**: porte direto de `recomendacao.ts` (protótipo) para uma função pura Python, mesmo padrão de 009/010/011 — mantém a auditabilidade exigida pelo FR-008.

## Complexity Tracking

*Sem violações de constituição a justificar.*
