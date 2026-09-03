# Phase 0 Research: Dados para o Mapa Interativo

## Formato de geometria na resposta

- **Decision**: usar `ST_AsGeoJSON` do PostGIS para serializar `geometria` diretamente em GeoJSON dentro da resposta JSON.
- **Rationale**: Leaflet.js consome GeoJSON nativamente; delega a conversão de formato ao banco, evitando reimplementar serialização de geometria em Python.
- **Alternatives considered**: serializar via Shapely em Python a partir do WKB — funcional, mas redundante quando o PostGIS já resolve isso nativamente com melhor performance.

## Escopo de dados retornados

- **Decision**: o endpoint agregador retorna apenas propriedades/talhões visíveis ao usuário autenticado (aplicando o mesmo filtro de RBAC da feature 014), nunca todos os dados do sistema.
- **Rationale**: consistência com o Princípio V (segurança) — um agrônomo não deve receber no payload do mapa dados de propriedades às quais não tem acesso, mesmo que a UI não os exiba.
