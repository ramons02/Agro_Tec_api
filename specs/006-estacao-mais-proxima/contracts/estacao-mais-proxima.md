# Contract: GET /api/v1/talhoes/{id}/estacao-mais-proxima

## Response 200

```json
{
  "status": "sucesso",
  "data_consulta_utc": "2026-09-03T12:00:00Z",
  "dados": {
    "estacao_codigo": "A901",
    "municipio": "Belém",
    "distancia_km": 12.4
  }
}
```

## Response 404 (talhão inexistente ou sem estações cadastradas)

```json
{"status": "erro", "codigo": 404, "mensagem": "Talhão não encontrado ou nenhuma estação disponível.", "detalhes": null}
```
