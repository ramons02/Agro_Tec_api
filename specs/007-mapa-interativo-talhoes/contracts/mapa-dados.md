# Contract: GET /api/v1/mapa/dados

## Response 200

Ver estrutura completa em `data-model.md`. Retorna apenas propriedades/talhões/estações visíveis ao usuário autenticado (RBAC da feature 014).

## Response 401

Token ausente/inválido — mesmo formato padrão da feature 001.
