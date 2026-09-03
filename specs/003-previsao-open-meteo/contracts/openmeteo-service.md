# Contract: Serviço de Previsão Open-Meteo (interno)

Serviço interno consumido por outras features — não expõe rota HTTP pública própria.

## Interface do serviço

```python
async def obter_previsao(latitude: float, longitude: float) -> PrevisaoClimatica
```

- **Entrada**: latitude/longitude do talhão.
- **Saída**: objeto `PrevisaoClimatica` (ver data-model.md) ou exceção de indisponibilidade, tratada pelo chamador conforme a política de fallback/cache expirado de cada feature consumidora.
- **Erro**: se o provedor não responder em 3s, lança uma exceção específica (`FontePrevisaoIndisponivelError`) para o chamador decidir o próximo passo (ex.: feature 002 tenta esta fonte como fallback; feature 010 usa o último valor em cache).
