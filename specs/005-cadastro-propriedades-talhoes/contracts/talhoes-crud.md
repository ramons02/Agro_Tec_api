# Contract: CRUD de Propriedades e Talhões

## POST /api/v1/propriedades

**Request**: `{"nome": "Fazenda Boa Esperança", "geometria": <GeoJSON Polygon | null>}`
**Response 201**: propriedade criada, incluindo `id`.

## GET /api/v1/propriedades?page=1&page_size=20

**Response 200**: lista paginada (RNF017 — 20 itens por página como padrão, acima de 50 itens exige paginação).

## POST /api/v1/talhoes

**Request**:
```json
{
  "propriedade_id": "uuid",
  "nome": "Talhão Norte",
  "geometria": { "type": "Polygon", "coordinates": [...] },
  "confirmar_fora_do_para": false
}
```

**Response 201**: talhão criado, com `area_ha` e (assincronamente ou em seguida) `tipo_solo`/CAD preenchidos pela feature 004.

**Response 409 (sobreposição na mesma propriedade)**:
```json
{"status": "erro", "codigo": 409, "mensagem": "Geometria sobrepõe o talhão 'Talhão Norte' na mesma propriedade.", "detalhes": {"tipo": "SOBREPOSICAO"}}
```

**Response 422 (fora do Pará, sem confirmação)**:
```json
{"status": "erro", "codigo": 422, "mensagem": "Talhão fora da área esperada do Pará. Confirme para prosseguir.", "detalhes": {"tipo": "FORA_DO_PARA", "requer_confirmacao": true}}
```

## POST /api/v1/talhoes/importar

**Request**: multipart com arquivo GeoJSON, KML ou Shapefile + `propriedade_id`.
**Response 201**: talhão criado a partir do primeiro polígono válido encontrado no arquivo.

## DELETE /api/v1/propriedades/{id}

**Response 204**: propriedade e todos os seus talhões excluídos (cascata).
