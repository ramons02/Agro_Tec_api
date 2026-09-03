# Data Model: Cadastro Territorial de Propriedades e Talhões

## Propriedade

| Campo | Tipo | Regras |
|---|---|---|
| `id` | UUID | chave primária |
| `nome` | string | obrigatório |
| `proprietario_id` | UUID | FK → `Usuario` (dono, RN018/feature 014) |
| `geometria` | geometry(Polygon, 4326) | opcional (uma propriedade pode não ter perímetro próprio desenhado, se só os talhões forem delimitados) |
| `criado_em` | timestamp | |

## Talhao

| Campo | Tipo | Regras |
|---|---|---|
| `id` | UUID | chave primária |
| `propriedade_id` | UUID | FK → `Propriedade`, `ON DELETE CASCADE` |
| `nome` | string | obrigatório |
| `geometria` | geometry(Polygon, 4326) | obrigatório, validado como polígono simples e não vazio |
| `area_ha` | numeric | calculada a partir da geometria (não informada manualmente) |
| *(campos de solo)* | — | ver feature 004 |

## Regras de validação (não persistidas, aplicadas no cadastro)

- Sobreposição de `geometria` com outro talhão da **mesma** `propriedade_id`, com área de interseção > 10m²: bloqueada.
- Sobreposição com talhão de propriedade **diferente**: permitida, com aviso.
- Centroide fora da bounding box aproximada do Pará: aceito mediante confirmação explícita do usuário.
