# Data Model: Balanço Hídrico do Solo

## BalancoHidricoDiario

| Campo | Tipo | Regras |
|---|---|---|
| `id` | bigserial | chave primária |
| `talhao_id` | UUID | FK → `Talhao` |
| `data` | date | único em conjunto com `talhao_id` |
| `armazenamento_mm` | numeric | sempre entre 0 e a CAD do talhão naquela data |
| `precipitacao_mm` | numeric | valor de entrada usado no cálculo (auditoria) |
| `evapotranspiracao_mm` | numeric | valor de entrada usado no cálculo (auditoria) |

**Índice**: `(talhao_id, data DESC)` para leitura rápida do valor mais recente (consumido pelas features 011/012).
