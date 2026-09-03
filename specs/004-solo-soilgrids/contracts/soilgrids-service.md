# Contract: Serviço de Parametrização de Solo (interno)

Acionado internamente pelo fluxo de cadastro de talhão (feature 005) — sem rota HTTP pública própria.

## Interface do serviço

```python
async def parametrizar_solo(latitude: float, longitude: float) -> PerfilSolo | None
```

- **Saída**: `PerfilSolo` com frações, `tipo_solo` e `capacidade_agua_disponivel_mm`, ou `None` se a coordenada não tiver cobertura na fonte (o talhão é salvo mesmo assim, com esses campos nulos).
