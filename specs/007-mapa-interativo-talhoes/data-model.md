# Data Model: Dados para o Mapa Interativo

Nenhuma entidade nova — agrega `Propriedade`, `Talhao` (com `status_plantio` da feature 011), `EstacaoInmet` e a última `MedicaoClima` de cada estação (feature 002), todas já modeladas em features anteriores.

## Objeto de resposta agregado (não persistido)

```json
{
  "propriedades": [{"id": "...", "nome": "...", "talhoes": [
    {"id": "...", "nome": "...", "geometria_geojson": {...}, "status_plantio": "VERDE"}
  ]}],
  "estacoes": [
    {"codigo": "A901", "municipio": "Belém", "posicao_geojson": {...}, "ultima_medicao": {"chuva_mm": 12.4, "vento_kmh": 7.2, "fonte_dados": "AO_VIVO"}}
  ]
}
```
