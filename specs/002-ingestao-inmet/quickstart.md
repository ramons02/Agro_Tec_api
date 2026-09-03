# Quickstart: Ingestão Assíncrona de Dados das Estações do INMET

## Pré-requisitos

- Backend rodando com o agendador ativo
- Ao menos uma estação cadastrada em `estacoes_inmet`

## Cenário 1 — Ingestão bem-sucedida

Aguardar um ciclo do job (ou disparar manualmente via endpoint interno, se implementado) e consultar:

```sql
SELECT * FROM medicoes_clima ORDER BY data_hora_utc DESC LIMIT 5;
```

**Esperado**: novas linhas com `fonte_dados = 'AO_VIVO'` para as estações ativas.

## Cenário 2 — Fallback por timeout

Simular indisponibilidade do INMET (ex.: apontar a URL do serviço para um host que não responde) e rodar o job.

**Esperado**: em até 3 segundos por estação, a medição é obtida via Open-Meteo para a mesma coordenada, e persistida normalmente.

## Cenário 3 — Sem duplicidade

Rodar o job duas vezes seguidas sem novo dado publicado pela fonte.

**Esperado**: nenhuma linha duplicada em `medicoes_clima` para o mesmo par (estação, instante).

## Validação de sucesso

Feature validada quando os 3 cenários se comportam conforme descrito, sem bloquear outras requisições da API durante a execução do job.
