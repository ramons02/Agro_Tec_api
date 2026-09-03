# Quickstart: Dados para o Mapa Interativo

## Cenário 1 — Payload consumível pelo Leaflet

```bash
curl http://localhost:8000/api/v1/mapa/dados -H "Authorization: Bearer $TOKEN"
```

**Esperado**: HTTP 200 com geometrias em GeoJSON válido (testável colando o valor de `geometria_geojson` em um validador de GeoJSON) e status de plantio presente em cada talhão.

## Cenário 2 — Escopo por RBAC

Consultar com um token de agrônomo vinculado a apenas uma propriedade.

**Esperado**: a resposta contém apenas essa propriedade, nunca as demais do sistema.

## Validação de sucesso

Feature validada quando o payload é consumido sem erros pelo componente de mapa do `Agro_Tec_app` e respeita o escopo de RBAC do usuário.
