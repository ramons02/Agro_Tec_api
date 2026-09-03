# Contract: GET /api/v1/clima/atual

**Query params**: `talhao_id` (obrigatório), `_t` (timestamp de bypass de cache do cliente, ignorado pelo backend além de logging)

**Headers de resposta (sempre presentes)**: `Cache-Control: no-cache, no-store, must-revalidate`, `Pragma: no-cache`

## Response 200 (dado ao vivo)

```json
{"status": "sucesso", "data_consulta_utc": "...", "dados": {"estacao": "A901", "chuva_mm": 12.4, "vento_kmh": 7.2, "fonte_dados": "AO_VIVO"}}
```

## Response 200 (cache expirado, todas as fontes falharam)

```json
{"status": "sucesso", "data_consulta_utc": "...", "dados": {"estacao": "A901", "chuva_mm": 8.0, "vento_kmh": 5.1, "fonte_dados": "CACHE_EXPIRADO", "medido_em_utc": "2026-09-03T09:00:00Z"}}
```
