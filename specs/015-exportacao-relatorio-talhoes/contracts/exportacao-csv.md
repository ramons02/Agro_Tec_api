# Contract: GET /api/v1/dashboard/plantio/exportar.csv

**Query params**: mesmos filtros do Dashboard (`propriedade_id`, `status`) — sem paginação, retorna todos os itens filtrados.

**Headers de resposta**: `Content-Type: text/csv; charset=utf-8`, `Content-Disposition: attachment; filename="talhoes.csv"`

**Corpo (exemplo)**:
```csv
propriedade,talhao,area_ha,tipo_solo,status_plantio,umidade_0_7cm_pct
Fazenda Boa Esperança,Talhão Norte,12.40,ARGILOSO,VERDE,34.20
```

(precedido por BOM UTF-8, `﻿`)

## Response 200 (sem talhões para exportar)

CSV apenas com o cabeçalho — o frontend (`Agro_Tec_app`) é responsável por desabilitar o botão nesse caso (FR-004), não este endpoint.
