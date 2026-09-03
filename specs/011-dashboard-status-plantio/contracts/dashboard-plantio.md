# Contract: GET /api/v1/dashboard/plantio

**Query params**: `propriedade_id` (opcional), `status` (opcional: `VERDE`\|`AMARELO`\|`VERMELHO`), `page` (default 1), `page_size` (default 20, máx. conforme RNF017)

## Response 200

```json
{
  "status": "sucesso",
  "data_consulta_utc": "...",
  "dados": {
    "itens": [
      {"talhao_id": "...", "nome": "Talhão Norte", "propriedade": "Fazenda Boa Esperança", "area_ha": 12.4, "tipo_solo": "ARGILOSO", "status_plantio": "VERDE", "percentual_cad": 74.5, "armazenamento_mm": 44.7}
    ],
    "total": 87,
    "page": 1,
    "page_size": 20
  }
}
```
