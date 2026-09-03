# Data Model: Clima em Tempo Real sem Cache Expirado

Nenhuma entidade nova — consulta `MedicaoClima` (feature 002) mais recente da estação associada ao talhão (feature 006).

## Objeto de resposta (não persistido)

| Campo | Tipo |
|---|---|
| `estacao_codigo` | string |
| `chuva_mm`, `umidade_pct` | numeric |
| `vento_kmh`, `rajada_kmh` | numeric — convertidos de `vento_velocidade_ms`/`vento_rajada_ms` (persistidos em m/s, feature 002) via $\times 3,6$ (ver research.md) |
| `fonte_dados` | `AO_VIVO` \| `CACHE_EXPIRADO` |
| `medido_em_utc` | timestamp |
