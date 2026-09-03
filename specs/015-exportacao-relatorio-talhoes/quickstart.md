# Quickstart: Exportação de Relatório de Talhões

## Cenário 1 — Exportação básica

```bash
curl "http://localhost:8000/api/v1/dashboard/plantio/exportar.csv" -H "Authorization: Bearer $TOKEN" -o talhoes.csv
```

**Esperado**: arquivo CSV com cabeçalho e uma linha por talhão visível ao usuário, abrindo corretamente acentuação em português.

## Cenário 2 — Exportação respeitando filtro

```bash
curl "http://localhost:8000/api/v1/dashboard/plantio/exportar.csv?status=VERMELHO" -H "Authorization: Bearer $TOKEN" -o risco.csv
```

**Esperado**: apenas talhões em status Vermelho no arquivo, mesmo que existam mais de 20 (sem paginação).

## Validação de sucesso

Feature validada quando os 2 cenários geram exatamente o conjunto esperado, com encoding correto.
