# Contract: GET /api/v1/talhoes/{id}/pulverizacao

## Response 200

```json
{
  "status": "sucesso",
  "data_consulta_utc": "2026-09-03T12:00:00Z",
  "dados": {
    "classificacao": "FAVORAVEL",
    "vento_kmh": 7.2,
    "rajada_kmh": 10.1,
    "fonte_dados": "AO_VIVO"
  }
}
```

`classificacao` é um dos três valores: `FAVORAVEL`, `BLOQUEIO_VENTO_FORTE`, `BLOQUEIO_INVERSAO_TERMICA`.
