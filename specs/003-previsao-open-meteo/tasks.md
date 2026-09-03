# Tasks: Integração com Open-Meteo para Previsão Climática e Solo

**Input**: Design documents from `/specs/003-previsao-open-meteo/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: não solicitados explicitamente — omitidos; validação via `quickstart.md`.

## Phase 1: Setup

- [ ] T001 [P] Configurar cache em memória (`cachetools.TTLCache`, TTL 30min) ou Redis, conforme disponibilidade de infraestrutura, em `app/services/openmeteo_service.py`

## Phase 2: Foundational

- [ ] T002 Definir schema Pydantic `PrevisaoClimatica` (vento 10m/100m, ET0, umidade do solo em 4 profundidades, `obtido_em_utc`) em `app/services/openmeteo_service.py` conforme `data-model.md`

**Checkpoint**: tipos prontos para a implementação do cliente.

---

## Phase 3: User Story 1 - Obter previsão para um talhão específico (Priority: P1) 🎯 MVP

**Goal**: previsão de vento, ET0 e umidade do solo disponível para qualquer coordenada do Pará.

**Independent Test**: chamar `obter_previsao(lat, long)` para uma coordenada válida (Cenário 1 do quickstart).

### Implementation for User Story 1

- [ ] T003 [US1] Implementar `obter_previsao(latitude, longitude)` em `app/services/openmeteo_service.py`: chamada `httpx` assíncrona ao endpoint de previsão horária com os parâmetros `wind_speed_10m`, `wind_speed_100m`, `et0_fao_evapotranspiration`, `soil_moisture_0_to_7cm` (e demais camadas)
- [ ] T004 [US1] Implementar parsing da resposta para o schema `PrevisaoClimatica`
- [ ] T005 [US1] Levantar `FontePrevisaoIndisponivelError` em caso de timeout (3.0s) ou erro HTTP

**Checkpoint**: previsão funcional para chamadas diretas ao serviço.

---

## Phase 4: User Story 2 - Operar dentro do limite gratuito (Priority: P2)

**Goal**: volume de chamadas reais permanece abaixo de 10.000/dia.

**Independent Test**: disparar múltiplas consultas para coordenadas próximas e confirmar reuso via cache (Cenário 2 do quickstart).

### Implementation for User Story 2

- [ ] T006 [US2] Implementar chave de cache por `(lat arredondada, long arredondada, hora)` com TTL de 30min em `obter_previsao`
- [ ] T007 [US2] Adicionar contador/log de chamadas reais à API para observabilidade do volume diário

**Checkpoint**: volume de chamadas visivelmente reduzido para coordenadas próximas.

---

## Phase Final: Polish

- [ ] T008 [P] Escrever testes com fixtures de payload real da Open-Meteo em `tests/contract/test_openmeteo_service.py`
- [ ] T009 Rodar os 2 cenários de `quickstart.md`

## Dependencies & Execution Order

Setup → Foundational → US1 → US2 → Polish. US2 estende US1 sem alterá-la (adiciona cache ao redor da chamada já implementada).

## Parallel Example

```bash
Task: "T003 Implementar obter_previsao"
Task: "T001 Configurar cache"
```

## Implementation Strategy

MVP = US1 (previsão funcional, mesmo sem cache). US2 (cache/limite) deve ser implementado antes de ir para produção com múltiplos talhões, mas não bloqueia o desenvolvimento/teste inicial das features que consomem esta (002, 004, 010).
