# Tasks: Cadastro Territorial de Propriedades e Talhões com Polígonos

**Input**: Design documents from `/specs/005-cadastro-propriedades-talhoes/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md; feature 001 (auth) implementada

**Tests**: não solicitados explicitamente — omitidos; validação via `quickstart.md`.

## Phase 1: Setup

- [ ] T001 Adicionar dependências `GeoAlchemy2`, `Shapely`, `GeoPandas` (ou `fiona`/`pyshp`), suporte a KML ao projeto
- [ ] T002 [P] Habilitar extensão PostGIS no banco (`CREATE EXTENSION IF NOT EXISTS postgis;`) via migração

## Phase 2: Foundational

- [ ] T003 Criar modelos `Propriedade` e `Talhao` em `app/db/models/` (geometria `Polygon`, SRID 4326) conforme `data-model.md`
- [ ] T004 [P] Criar migração Alembic com FK `talhoes.propriedade_id -> propriedades.id ON DELETE CASCADE` e índices GiST em `geometria`
- [ ] T005 [P] Implementar `core/geo/validacao_geometria.py`: funções puras `verifica_sobreposicao(poligono, outros_poligonos)` e `esta_dentro_do_para(centroide)` (Shapely), portadas de `validacaoGeometria.ts`

**Checkpoint**: schema espacial pronto.

---

## Phase 3: User Story 1 - Cadastrar propriedade e talhão com polígono (Priority: P1) 🎯 MVP

**Goal**: CRUD funcional de propriedades e talhões vinculados, com deleção em cascata.

**Independent Test**: criar propriedade, criar talhão vinculado, excluir a propriedade e confirmar cascata (Cenários 1 e 4 do quickstart).

### Implementation for User Story 1

- [ ] T006 [P] [US1] Implementar schemas Pydantic `PropriedadeCreate`/`PropriedadeRead` em `app/api/v1/endpoints/propriedades.py`
- [ ] T007 [P] [US1] Implementar schemas Pydantic `TalhaoCreate`/`TalhaoRead` em `app/api/v1/endpoints/talhoes.py`
- [ ] T008 [US1] Implementar CRUD de `POST/GET/PUT/DELETE /api/v1/propriedades` (contrato em `contracts/talhoes-crud.md`)
- [ ] T009 [US1] Implementar CRUD de `POST/GET/PUT/DELETE /api/v1/talhoes`, calculando `area_ha` a partir da geometria (`ST_Area` com projeção métrica)
- [ ] T010 [US1] Registrar as rotas em `app/api/v1/router.py`, protegidas por `Depends(get_current_user)` (feature 001)

**Checkpoint**: CRUD básico funcional, sem ainda as validações de negócio.

---

## Phase 4: User Story 2 - Importar geometria de arquivo (Priority: P2)

**Goal**: talhão criado a partir de um arquivo GeoJSON/KML/Shapefile.

**Independent Test**: importar um arquivo válido e confirmar o polígono resultante (Cenário do quickstart de importação).

### Implementation for User Story 2

- [ ] T011 [P] [US2] Implementar `app/services/importacao_geo_service.py`: parsing de GeoJSON, KML e Shapefile, extraindo o primeiro polígono válido
- [ ] T012 [US2] Implementar endpoint `POST /api/v1/talhoes/importar` (multipart) reaproveitando o CRUD de talhão do T009

**Checkpoint**: importação funcional para os 3 formatos.

---

## Phase 5: User Story 3 - Impedir sobreposição indevida (Priority: P2)

**Goal**: sobreposição dentro da mesma propriedade bloqueada; entre propriedades diferentes, permitida com aviso; fora do Pará aceito com confirmação.

**Independent Test**: Cenários 2 e 3 do `quickstart.md`.

### Implementation for User Story 3

- [ ] T013 [US3] No `POST /talhoes` (T009), antes de persistir: consultar talhões da mesma propriedade via `ST_Overlaps` + `ST_Area(ST_Intersection(...)) > 10`; se sobrepor, retornar 409 (`contracts/talhoes-crud.md`)
- [ ] T014 [US3] Verificar centroide contra a bounding box aproximada do Pará (`esta_dentro_do_para`, T005); se fora e sem `confirmar_fora_do_para=true`, retornar 422 pedindo confirmação
- [ ] T015 [US3] Permitir e sinalizar (campo de aviso na resposta) sobreposição entre talhões de propriedades diferentes, sem bloquear

**Checkpoint**: todas as regras de validação geométrica ativas.

---

## Phase Final: Polish

- [ ] T016 [P] Implementar paginação (`page`, `page_size`, default 20) em `GET /propriedades` e `GET /talhoes` (RNF017)
- [ ] T017 [P] Escrever testes unitários de `validacao_geometria.py` (casos de fronteira de área de sobreposição) em `tests/unit/test_validacao_geometria.py`
- [ ] T018 [P] Escrever testes de contrato do CRUD em `tests/contract/test_propriedades_crud.py` e `test_talhoes_crud.py`
- [ ] T019 Rodar os 4 cenários de `quickstart.md`

## Dependencies & Execution Order

Setup → Foundational → US1 → US2/US3 (podem ser paralelas, ambas estendem o endpoint de criação de talhão de US1) → Polish

## Parallel Example

```bash
Task: "T011 Implementar importacao_geo_service.py"     # US2
Task: "T013 Implementar checagem de sobreposicao"       # US3
```

## Implementation Strategy

MVP = US1 (CRUD básico). US2 (importação) e US3 (validações) são incrementos que podem ser entregues depois sem quebrar o cadastro manual básico — mas US3 é fortemente recomendada antes de abrir para uso real, para não permitir dados geometricamente inválidos se acumularem.
