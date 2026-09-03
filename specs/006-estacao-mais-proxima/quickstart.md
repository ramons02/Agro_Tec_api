# Quickstart: Estação INMET Mais Próxima

## Cenário 1 — Estação mais próxima correta

Com duas estações a distâncias conhecidas de um talhão de teste, consultar `/talhoes/{id}/estacao-mais-proxima`.

**Esperado**: retorna a estação de menor distância real, com o valor em km batendo (com pequena tolerância) com o cálculo geodésico esperado.

## Cenário 2 — Performance

Medir o tempo de resposta da consulta com o índice GiST criado.

**Esperado**: menos de 100ms na maioria das execuções (RNF003).

## Validação de sucesso

Feature validada quando a estação correta é sempre retornada e o tempo de resposta atende ao SLA de 100ms.
