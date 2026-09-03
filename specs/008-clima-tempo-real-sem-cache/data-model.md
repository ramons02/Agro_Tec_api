# Data Model: Clima em Tempo Real sem Cache Expirado

Nenhuma entidade nova — consulta `MedicaoClima` (feature 002) mais recente da estação associada ao talhão (feature 006).

## Objeto de resposta (não persistido)

| Campo | Tipo |
|---|---|
| `estacao_codigo` | string |
| `chuva_mm`, `vento_kmh`, `umidade_pct` | numeric |
| `fonte_dados` | `AO_VIVO` \| `CACHE_EXPIRADO` |
| `medido_em_utc` | timestamp |
