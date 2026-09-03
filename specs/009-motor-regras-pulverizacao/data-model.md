# Data Model: Motor de Regras de Pulverização

Nenhuma entidade persistida — apenas um tipo de resultado.

## ClassificacaoPulverizacao (enum, não persistido)

- `FAVORAVEL`
- `BLOQUEIO_VENTO_FORTE`
- `BLOQUEIO_INVERSAO_TERMICA`

Derivado a partir de `vento_kmh` e `rajada_kmh` da leitura mais recente da estação mais próxima do talhão (features 006/008).
