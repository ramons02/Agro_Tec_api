# Quickstart: Dashboard de Status de Plantio

## Cenário 1 — Listagem sem filtro

```bash
curl "http://localhost:8000/api/v1/dashboard/plantio" -H "Authorization: Bearer $TOKEN"
```

**Esperado**: talhões visíveis ao usuário, cada um com `status_plantio` entre os três valores válidos.

## Cenário 2 — Filtro por status

```bash
curl "http://localhost:8000/api/v1/dashboard/plantio?status=VERMELHO" -H "Authorization: Bearer $TOKEN"
```

**Esperado**: apenas talhões em risco (Vermelho).

## Cenário 3 — Paginação

Com mais de 50 talhões cadastrados, consultar sem `page_size`.

**Esperado**: resposta com 20 itens e `total` refletindo o total real.

## Validação de sucesso

Feature validada quando os 3 cenários retornam exatamente o subconjunto esperado.
