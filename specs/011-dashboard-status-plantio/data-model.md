# Data Model: Dashboard de Status de Plantio

## Extensão de BalancoHidricoDiario (feature 010)

| Campo novo | Tipo | Regras |
|---|---|---|
| `status_plantio` | enum | `VERDE` \| `AMARELO` \| `VERMELHO`, calculado no mesmo job diário |

Nenhuma nova entidade — o dashboard lê `Talhao` (005) join `BalancoHidricoDiario` (010, registro mais recente por talhão).
