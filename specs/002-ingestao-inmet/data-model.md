# Data Model: Ingestão Assíncrona de Dados das Estações do INMET

## EstacaoInmet

| Campo | Tipo | Regras |
|---|---|---|
| `codigo` | string | chave primária, único (ex: "A901") |
| `nome` | string | nome/descrição da estação |
| `estado` | string(2) | fixo `"PA"` neste projeto |
| `posicao` | geometry(Point, 4326) | localização física da estação |

## MedicaoClima

| Campo | Tipo | Regras |
|---|---|---|
| `id` | bigserial | chave primária |
| `estacao_codigo` | string | FK → `EstacaoInmet.codigo` |
| `data_hora_utc` | timestamp | instante da medição; único em conjunto com `estacao_codigo` |
| `precipitacao_mm` | numeric(5,2) | |
| `temperatura_c` | numeric(4,2) | |
| `umidade_pct` | numeric(4,2) | |
| `vento_velocidade_ms` | numeric(4,2) | |
| `vento_rajada_ms` | numeric(4,2) | |
| `fonte_dados` | enum | `AO_VIVO` \| `CACHE_EXPIRADO` (RF033) |

**Índice**: `(estacao_codigo, data_hora_utc DESC)` para consulta de última medição rápida (usada pela feature 008).

**Retenção**: registros com `data_hora_utc` há mais de 12 meses são compactados em um agregado diário (estrutura de agregado fora do escopo desta tabela — a definir se necessário como tabela separada `medicoes_clima_diarias`).
