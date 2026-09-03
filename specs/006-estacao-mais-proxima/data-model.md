# Data Model: Identificação Espacial da Estação INMET Mais Próxima

Nenhuma entidade nova — esta feature consulta `Talhao` (feature 005) e `EstacaoInmet` (feature 002) já modeladas. Requisito de schema adicional:

| Índice | Tabela | Coluna | Tipo |
|---|---|---|---|
| `idx_estacoes_inmet_posicao` | `estacoes_inmet` | `posicao` | GiST |
| `idx_talhoes_geometria` | `talhoes` | `geometria` | GiST |

## Objeto de resposta (não persistido)

| Campo | Tipo |
|---|---|
| `estacao_codigo` | string |
| `municipio` | string |
| `distancia_km` | float |
