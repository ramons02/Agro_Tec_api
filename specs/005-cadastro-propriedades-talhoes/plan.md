# Implementation Plan: Cadastro Territorial de Propriedades e Talhões com Polígonos

**Branch**: `005-cadastro-propriedades-talhoes` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-cadastro-propriedades-talhoes/spec.md`

## Summary

CRUD de `propriedades` e `talhoes` com geometria `Polygon` (SRID 4326) validada no PostGIS, deleção em cascata de talhões ao excluir a propriedade, importação de geometria via GeoJSON/KML/Shapefile, bloqueio de sobreposição dentro da mesma propriedade (`ST_Overlaps` > 10m²) e aceite com confirmação para talhão fora do Pará.

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI, Pydantic v2, SQLAlchemy Async + GeoAlchemy2 (extensão espacial), Shapely/GeoPandas (parsing de GeoJSON/KML/Shapefile)

**Storage**: PostgreSQL + PostGIS (`propriedades`, `talhoes`)

**Testing**: pytest + pytest-asyncio contra um banco PostGIS de teste; testes de contrato do CRUD e testes unitários de validação geométrica (sobreposição, fora do Pará)

**Target Platform**: Linux server (container)

**Project Type**: web-service (backend API)

**Performance Goals**: cadastro completo (com validação geométrica) em menos de 2 segundos (RNF002)

**Constraints**: geometria sempre SRID 4326 (Princípio IV); paginação obrigatória acima de 50 itens (RNF017)

**Scale/Scope**: potencialmente milhares de talhões por produtor ao longo do tempo, dezenas por propriedade

## Constitution Check

| Princípio | Avaliação |
|---|---|
| I. Assíncrono e Tipado | PASS — CRUD assíncrono, validação via Pydantic v2 |
| II. Custo Zero em Integrações Externas | N/A — sem integração externa nesta feature |
| III. Tempo Real sem Cache Obsoleto | N/A — dado cadastral, não climático |
| IV. Geoprocessamento Correto e Verificável | PASS — é o núcleo desta feature: `GEOMETRY(Polygon, 4326)`, `ST_Overlaps` nativo do PostGIS para sobreposição, nunca cálculo aproximado em aplicação |
| V. Segurança JWT e Segredos Fora do Git | PASS — todas as rotas exigem `Authorization: Bearer` (dependência da feature 001); autorização fina por dono é escopo da feature 014 |

Nenhuma violação. Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/005-cadastro-propriedades-talhoes/
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
│   ├── propriedades.py          # CRUD propriedades
│   └── talhoes.py                # CRUD talhões + importação de arquivo
├── core/
│   └── geo/
│       └── validacao_geometria.py  # sobreposição, bounding box do Pará (funções puras sobre Shapely)
├── services/
│   └── importacao_geo_service.py   # parsing GeoJSON/KML/Shapefile
└── db/models/
    ├── propriedade.py
    └── talhao.py

tests/
├── contract/
│   ├── test_propriedades_crud.py
│   └── test_talhoes_crud.py
└── unit/
    └── test_validacao_geometria.py
```

**Structure Decision**: validação geométrica isolada em `core/geo/` como funções puras testáveis sem banco, espelhando o padrão já validado no protótipo (`validacaoGeometria.ts`); a checagem de sobreposição real (com dados já persistidos) usa `ST_Overlaps` no banco.

## Complexity Tracking

*Sem violações de constituição a justificar.*
