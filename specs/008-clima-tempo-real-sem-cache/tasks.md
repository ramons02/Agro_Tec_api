# Tasks: Consulta de Dados Climáticos em Tempo Real sem Cache Expirado

**Input**: Design documents from `/specs/008-clima-tempo-real-sem-cache/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md; features 002, 003, 006 implementadas

**Tests**: não solicitados explicitamente — omitidos; validação via `quickstart.md`.

## Phase 1: Setup

- [X] T001 [P] Adicionar `asyncio.Lock` por estação (dicionário em memória) em `app/services/clima_tempo_real_service.py`, ou lock distribuído em Redis se múltiplas instâncias forem usadas

## Phase 2: Foundational

*Reaproveita modelos e serviços das features 002/003/006 — sem novo schema.*

## Phase 3: User Story 1 - Consulta sempre atualizada (Priority: P1) 🎯 MVP

**Goal**: `/clima/atual` nunca retorna dado com mais de 30min sem sinalizar ou buscar atualização.

**Independent Test**: Cenários 1, 2 e 3 do `quickstart.md`.

### Implementation for User Story 1

- [X] T002 [US1] Implementar `clima_tempo_real_service.obter_clima_atual(talhao_id)`: resolve estação mais próxima (feature 006), verifica idade da última medição
- [X] T003 [US1] Se medição ≤30min: retornar direto com `fonte_dados="AO_VIVO"`
- [X] T004 [US1] Se medição >30min: adquirir lock da estação, disparar busca imediata (INMET com fallback Open-Meteo, reaproveitando `inmet_service`/`openmeteo_service`), persistir e retornar
- [X] T005 [US1] Se todas as fontes falharem: retornar última medição válida com `fonte_dados="CACHE_EXPIRADO"` (RN017), nunca erro
- [X] T005b [US1] Implementar `converter_ms_para_kmh(velocidade_ms) -> float` (×3,6, `calculos-geo-metero.md` §2) em `app/core/calculos/pulverizacao.py` (mesmo módulo da feature 009) e aplicar a `vento_velocidade_ms`/`vento_rajada_ms` antes de montar a resposta — ver research.md
- [X] T006 [US1] Implementar endpoint `GET /api/v1/clima/atual?talhao_id=&_t=` aplicando headers `Cache-Control: no-cache, no-store, must-revalidate` e `Pragma: no-cache` (contrato em `contracts/clima-atual.md`), retornando `vento_kmh`/`rajada_kmh` já convertidos

**Checkpoint**: feature completa — história única.

---

## Phase Final: Polish

- [X] T007 [P] Escrever teste de concorrência (duas requisições simultâneas disparam uma única busca) em `tests/integration/test_clima_atual_integration.py` (Postgres real, não SQLite)
- [X] T008 [P] Escrever teste validando os headers no-cache no mesmo arquivo de integração acima
- [X] T009 Rodar os 3 cenários de `quickstart.md`

## Dependencies & Execution Order

Setup → US1 (Foundational vazio, reaproveita features anteriores) → Polish

## Implementation Strategy

Feature de história única — MVP e escopo completo coincidem.
