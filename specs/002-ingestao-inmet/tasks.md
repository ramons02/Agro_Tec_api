# Tasks: Ingestão Assíncrona de Dados das Estações do INMET

**Input**: Design documents from `/specs/002-ingestao-inmet/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md; feature 001 (auth) já implementada

**Tests**: não solicitados explicitamente — omitidos; validação via `quickstart.md`.

## Phase 1: Setup

- [ ] T001 Adicionar dependências `httpx`, `APScheduler` ao projeto (`pyproject.toml`)
- [ ] T002 [P] Criar `app/core/scheduler.py` com instância do APScheduler registrada no ciclo de vida da app (`lifespan` do FastAPI)

## Phase 2: Foundational

- [ ] T003 Criar modelos `EstacaoInmet` e `MedicaoClima` em `app/db/models/` conforme `data-model.md` (incluindo enum `fonte_dados`)
- [ ] T004 [P] Criar migração Alembic para `estacoes_inmet` e `medicoes_clima`, com índice `(estacao_codigo, data_hora_utc DESC)` e constraint de unicidade `(estacao_codigo, data_hora_utc)`
- [ ] T005 [P] Popular `estacoes_inmet` com a lista conhecida de estações automáticas do INMET no Pará (seed/migração de dados)

**Checkpoint**: schema pronto para receber medições.

---

## Phase 3: User Story 1 - Captura periódica de medições (Priority: P1) 🎯 MVP

**Goal**: medições reais das estações do Pará ficam disponíveis no banco.

**Independent Test**: disparar uma rodada de ingestão e consultar `medicoes_clima` (Cenário 1 do quickstart).

### Implementation for User Story 1

- [ ] T006 [P] [US1] Implementar `app/services/inmet_service.py`: cliente `httpx` assíncrono com timeout de 3.0s, parsing da resposta do INMET
- [ ] T007 [US1] Implementar upsert de medição (`ON CONFLICT DO NOTHING`) em `app/db/models/medicao_clima.py` ou em um repositório dedicado
- [ ] T008 [US1] Implementar o job de ingestão (`ingestar_todas_estacoes`) que itera as estações e persiste medições com `fonte_dados="AO_VIVO"`
- [ ] T009 [US1] Registrar o job no APScheduler com cadência de 10 minutos (ver research.md)

**Checkpoint**: ingestão funcionando e populando o banco sem duplicidade.

---

## Phase 4: User Story 2 - Continuidade quando o INMET falha (Priority: P2)

**Goal**: timeout do INMET aciona fallback para o Open-Meteo automaticamente.

**Independent Test**: simular indisponibilidade do INMET e confirmar que a medição chega via fallback (Cenário 2 do quickstart).

### Implementation for User Story 2

- [ ] T010 [US2] Capturar timeout/erro HTTP em `inmet_service.py` e levantar exceção específica (`FonteIndisponivelError`)
- [ ] T011 [US2] No job de ingestão, ao capturar `FonteIndisponivelError`, chamar o serviço da feature 003 (`openmeteo_service.obter_previsao`) para a coordenada da estação e persistir a medição equivalente
- [ ] T012 [US2] Registrar log estruturado de cada fallback acionado (estação, motivo, timestamp)

**Checkpoint**: fallback funcional — nenhuma estação fica sem dado só por falha pontual do INMET.

---

## Phase Final: Polish

- [ ] T013 [P] Implementar job diário de agregação/retenção (RNF014): compactar medições com mais de 12 meses em agregados diários
- [ ] T014 [P] Escrever testes unitários com mock de `httpx` para timeout e fallback em `tests/unit/test_fallback_timeout.py`
- [ ] T015 Rodar os 3 cenários de `quickstart.md`

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → Polish
- US2 depende da feature 003 (Open-Meteo) estar implementada para o fallback funcionar de ponta a ponta — pode ser desenvolvida em paralelo usando um mock do serviço 003 e integrada depois.

## Parallel Example

```bash
Task: "T006 Implementar inmet_service.py"
Task: "T005 Popular estacoes_inmet"
```

## Implementation Strategy

MVP = US1 (ingestão básica funcionando). US2 (fallback) é o incremento seguinte e pode ser adiado sem quebrar o MVP, desde que a indisponibilidade do INMET seja rara.
