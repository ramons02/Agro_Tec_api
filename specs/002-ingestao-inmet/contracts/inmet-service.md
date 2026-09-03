# Contract: Serviço de Ingestão INMET (interno)

Esta feature não expõe uma rota pública de escrita — o "contrato" é o comportamento do job de ingestão e, opcionalmente, um endpoint interno de status.

## GET /api/v1/interno/ingestao/status (opcional, uso operacional)

### Response 200

```json
{
  "status": "sucesso",
  "data_consulta_utc": "2026-09-03T12:00:00Z",
  "dados": {
    "ultima_execucao_utc": "2026-09-03T11:50:00Z",
    "estacoes_com_sucesso": 42,
    "estacoes_com_fallback": 1,
    "estacoes_com_falha_total": 0
  }
}
```

## Comportamento esperado do job (não é uma rota HTTP)

- Para cada estação em `estacoes_inmet`, busca a medição mais recente no INMET.
- Timeout de 3.0s por estação; no timeout, delega a busca equivalente ao serviço da feature 003 (Open-Meteo) para a mesma coordenada.
- Se ambas as fontes falharem, nenhuma medição nova é gravada para aquela estação neste ciclo (o RN017 de retorno de cache expirado é responsabilidade da feature 008 no momento da consulta, não da ingestão).
