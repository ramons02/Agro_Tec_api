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

## Conversão de unidade de vento (m/s → km/h)

- **Decision**: `medicoes_clima` persiste vento e rajada em m/s (unidade nativa do INMET, conforme schema oficial do Escopo Técnico); esta feature converte para km/h ($v_{km/h} = v_{m/s} \times 3,6$, `calculos-geo-metero.md` §2) no momento de montar a resposta de `/clima/atual`, antes de repassar o valor a qualquer consumidor (mapa, motor de pulverização).
- **Rationale**: mantém a tabela fiel à unidade original da fonte (boa prática de auditoria/rastreabilidade), e centraliza a conversão em um único ponto de leitura, evitando que cada feature consumidora (007, 009) reimplemente a mesma multiplicação por 3,6 de forma potencialmente inconsistente.
- **Alternatives considered**: persistir já em km/h — descartado por divergir do schema oficial do Escopo Técnico (`vento_velocidade_ms`, `vento_rajada_ms`) sem necessidade.
