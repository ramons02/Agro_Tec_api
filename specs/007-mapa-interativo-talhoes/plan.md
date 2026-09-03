# Implementation Plan: Visualização Interativa de Talhões e Estações em Mapa

**Branch**: `007-mapa-interativo-talhoes` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-mapa-interativo-talhoes/spec.md`

## Summary

Esta feature é majoritariamente de **frontend** (renderização Leaflet.js) e sua implementação de UI pertence ao repositório `Agro_Tec_app`, fora do escopo deste repositório (`Agro_Tec_api`). O que cabe a este repositório é expor os dados necessários para o mapa: geometrias de propriedades/talhões em GeoJSON, status de plantio por talhão, posição das estações e últimas medições — tudo já produzido pelas features 005, 006, 002 e 011. Este plano cobre apenas o endpoint agregador de dados do mapa.

## Technical Context

**Language/Version**: Python 3.12

**Primary Dependencies**: FastAPI, GeoAlchemy2 (serialização de geometria para GeoJSON)

**Storage**: leitura de `propriedades`, `talhoes`, `estacoes_inmet`, `medicoes_clima` (sem nova tabela)

**Testing**: pytest + pytest-asyncio (contrato do endpoint agregador)

**Target Platform**: Linux server (container)

**Project Type**: web-service (backend API) — a camada de mapa/Leaflet.js em si é responsabilidade do repositório `Agro_Tec_app`

**Performance Goals**: resposta do endpoint agregador em menos de 2 segundos (RNF002) mesmo com centenas de talhões

**Constraints**: geometria sempre servida em GeoJSON/SRID 4326 para consumo direto por Leaflet.js

**Scale/Scope**: todos os talhões e estações visíveis na área de interesse do usuário autenticado

## Constitution Check

| Princípio | Avaliação |
|---|---|
| I. Assíncrono e Tipado | PASS — endpoint assíncrono, resposta tipada via Pydantic v2 |
| II. Custo Zero em Integrações Externas | N/A |
| III. Tempo Real sem Cache Obsoleto | PASS — últimas medições exibidas seguem a mesma regra de staleness da feature 008 |
| IV. Geoprocessamento Correto e Verificável | PASS — geometrias servidas diretamente do PostGIS (`ST_AsGeoJSON`), sem transformação própria |
| V. Segurança JWT e Segredos Fora do Git | PASS — endpoint exige autenticação; retorna apenas propriedades visíveis ao usuário (RBAC da feature 014) |

Nenhuma violação. Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/007-mapa-interativo-talhoes/
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
└── api/v1/endpoints/
    └── mapa.py                   # GET /mapa/dados — agrega talhões (GeoJSON + status), estações e últimas medições

tests/
└── contract/
    └── test_mapa_dados.py
```

**Estrutura no repositório `Agro_Tec_app` (fora de escopo aqui, citada apenas para rastreabilidade)**:
```text
src/components/MapaTalhoes.tsx
src/components/MapaDesenhoTalhao.tsx
```

**Structure Decision**: um único endpoint agregador de leitura, para evitar que o frontend precise orquestrar múltiplas chamadas (propriedades + estações + medições) para montar o mapa.

## Complexity Tracking

*Sem violações de constituição a justificar. Nota de escopo: a renderização visual (cores por status, popups, responsividade) é responsabilidade do `Agro_Tec_app` e não tem tasks de implementação neste repositório.*
