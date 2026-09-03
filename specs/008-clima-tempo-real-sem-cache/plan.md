# Implementation Plan: Consulta de Dados Climáticos em Tempo Real sem Cache Expirado

**Branch**: `008-clima-tempo-real-sem-cache` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-clima-tempo-real-sem-cache/spec.md`

## Summary

Endpoint `/api/v1/clima/atual` que verifica a idade da última medição local de um talhão (via sua estação mais próxima); se ≤30min, retorna direto; se >30min, dispara busca imediata (INMET com fallback Open-Meteo, feature 002/003) antes de responder. Toda resposta inclui `fonte_dados`. Cabeçalhos de não-cache aplicados na resposta HTTP.

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI, SQLAlchemy Async, httpx (via serviços das features 002/003), `asyncio.Lock` (ou lock distribuído) por estação para evitar buscas concorrentes duplicadas

**Storage**: leitura de `medicoes_clima` (feature 002)

**Testing**: pytest + pytest-asyncio, incluindo teste de concorrência (duas requisições simultâneas para a mesma estação expirada disparam apenas uma busca)

**Target Platform**: Linux server (container)

**Project Type**: web-service (backend API)

**Performance Goals**: resposta em até 2s no caminho feliz (medição já fresca); até 2s + tempo de busca (até 3s de timeout) quando expirada

**Constraints**: cabeçalhos `Cache-Control: no-cache, no-store, must-revalidate` e `Pragma: no-cache` em toda resposta desta rota (Convenção Técnica §3.2)

**Scale/Scope**: consultas frequentes por múltiplos usuários simultaneamente sobre o mesmo conjunto de talhões/estações

## Constitution Check

| Princípio | Avaliação |
|---|---|
| I. Assíncrono e Tipado | PASS — toda a cadeia (verificação de idade, busca, resposta) é assíncrona |
| II. Custo Zero em Integrações Externas | PASS — reaproveita as fontes já gratuitas das features 002/003 |
| III. Tempo Real sem Cache Obsoleto | PASS — é a própria feature que implementa este princípio central da Constituição |
| IV. Geoprocessamento Correto e Verificável | N/A — usa a estação já resolvida pela feature 006 |
| V. Segurança JWT e Segredos Fora do Git | PASS — rota autenticada |

Nenhuma violação. Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/008-clima-tempo-real-sem-cache/
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
│   └── clima.py                  # GET /clima/atual?talhao_id=&_t= — aplica headers no-cache
└── services/
    └── clima_tempo_real_service.py  # verifica staleness, orquestra busca imediata + lock por estação

tests/
├── contract/
│   └── test_clima_atual.py
└── unit/
    └── test_staleness_e_concorrencia.py
```

**Structure Decision**: serviço dedicado que orquestra as fontes já existentes (002/003), mantendo a rota HTTP fina (apenas cabeçalhos + delegação).

## Complexity Tracking

*Sem violações de constituição a justificar.*
