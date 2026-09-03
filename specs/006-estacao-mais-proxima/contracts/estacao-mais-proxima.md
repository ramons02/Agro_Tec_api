# Contract: GET /api/v1/talhoes/{id}/estacao-mais-proxima

**Atualização (Escopo V3, 2026-09-03)**: retorna as até 3 estações mais
próximas (era 1), usadas na interpolação IDW de `calculos-geo-metero.md` §1B.

## Response 200

```json
{
  "status": "sucesso",
  "data_consulta_utc": "2026-09-03T12:00:00Z",
  "dados": {
    "estacoes": [
      {"estacao_codigo": "A901", "municipio": "Belém", "distancia_km": 12.4},
      {"estacao_codigo": "A902", "municipio": "Ananindeua", "distancia_km": 18.7},
      {"estacao_codigo": "A903", "municipio": "Marituba", "distancia_km": 25.1}
    ]
  }
}
```

Com menos de 3 estações cadastradas na área, a lista vem com as disponíveis (mínimo 1).

## Response 404 (talhão inexistente ou sem estações cadastradas)

```json
{"status": "erro", "codigo": 404, "mensagem": "Talhão não encontrado ou nenhuma estação disponível.", "detalhes": null}
```
