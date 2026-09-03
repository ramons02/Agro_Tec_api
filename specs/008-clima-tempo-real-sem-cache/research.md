# Phase 0 Research: Clima em Tempo Real sem Cache Expirado

## Evitar buscas concorrentes duplicadas

- **Decision**: lock assíncrono por `estacao_codigo` (em memória via `asyncio.Lock` para um único processo; ou lock distribuído em Redis se houver múltiplas instâncias do backend) durante a busca imediata.
- **Rationale**: evita que N usuários consultando o mesmo talhão no mesmo instante de expiração disparem N buscas externas simultâneas, desperdiçando cota de chamadas gratuitas (Princípio II).
- **Alternatives considered**: sem lock, aceitando chamadas duplicadas ocasionais — rejeitado por poder somar rapidamente ao limite diário do Open-Meteo em picos de uso.

## Cabeçalhos HTTP de não-cache

- **Decision**: aplicar `Cache-Control: no-cache, no-store, must-revalidate` e `Pragma: no-cache` na resposta do backend (não apenas confiar no parâmetro `_t` do frontend).
- **Rationale**: defesa em profundidade — mesmo que o frontend (fora deste repositório) não implemente o parâmetro corretamente, o backend garante que proxies/CDNs intermediários não cacheiem a resposta.

## Falha simultânea de todas as fontes

- **Decision**: retornar a última medição válida com `fonte_dados: "CACHE_EXPIRADO"` e HTTP 200 (não um erro), conforme RN017.
- **Rationale**: requisito explícito já validado — nunca um erro bloqueante para o usuário final.
