# Contract: GET /api/v1/talhoes/{id}/pulverizacao

**Atualização (Escopo V3, 2026-09-03)**: além da regra de vento, agora também
valida Delta T (RN021/RN022) — checagem complementar, não substituta.

## Response 200

```json
{
  "status": "sucesso",
  "data_consulta_utc": "2026-09-03T12:00:00Z",
  "dados": {
    "classificacao": "FAVORAVEL",
    "motivos_bloqueio": [],
    "vento_kmh": 7.2,
    "rajada_kmh": 10.1,
    "delta_t_c": 4.3,
    "fonte_dados": "AO_VIVO"
  }
}
```

`classificacao` é um dos quatro valores: `FAVORAVEL`, `BLOQUEIO_VENTO_FORTE`,
`BLOQUEIO_INVERSAO_TERMICA`, `BLOQUEIO_EVAPORACAO_EXCESSIVA` — reflete o
primeiro motivo de bloqueio encontrado (vento checado antes de Delta T).
`motivos_bloqueio` lista **todos** os motivos de bloqueio ativos (pode ter
vento e Delta T bloqueando ao mesmo tempo, por razões diferentes).
`delta_t_c` é `null` quando não há temperatura/umidade disponíveis para
calculá-lo (a classificação nesse caso considera só o vento).
