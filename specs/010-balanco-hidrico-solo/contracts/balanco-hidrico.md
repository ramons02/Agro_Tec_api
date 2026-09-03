# Contract: Cálculo de Balanço Hídrico (interno)

Job interno, sem rota HTTP pública própria. Interface da função pura reaproveitável em testes e por outras features:

```python
def calcular_armazenamento(
    arm_anterior_mm: float,
    precipitacao_mm: float,
    et0_mm: float,
    kc: float,
    cad_mm: float,
) -> float:
    """Retorna o novo armazenamento, sempre em [0, cad_mm]."""
```

## GET /api/v1/talhoes/{id}/balanco-hidrico (leitura, opcional/debug)

### Response 200

```json
{"status": "sucesso", "data_consulta_utc": "...", "dados": {"data": "2026-09-03", "armazenamento_mm": 42.1, "cad_mm": 60.0, "percentual_cad": 70.2}}
```
