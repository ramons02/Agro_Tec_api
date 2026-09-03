# Quickstart: Integração com Open-Meteo

## Cenário 1 — Previsão para coordenada válida no Pará

```python
previsao = await obter_previsao(latitude=-1.4558, longitude=-48.4902)
```

**Esperado**: objeto com vento 10m/100m, ET0 e umidade do solo em 4 profundidades preenchidos.

## Cenário 2 — Respeito ao limite de chamadas

Disparar 50 consultas para coordenadas muito próximas entre si (mesmo talhão/vizinhança) em um curto intervalo.

**Esperado**: o número de chamadas reais à Open-Meteo é bem menor que 50, graças ao cache por coordenada+hora.

## Validação de sucesso

Feature validada quando a previsão retorna estruturada corretamente e o volume de chamadas externas fica visivelmente abaixo do número de consultas lógicas feitas pelos consumidores.
