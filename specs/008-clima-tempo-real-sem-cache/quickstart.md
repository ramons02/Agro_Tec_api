# Quickstart: Clima em Tempo Real sem Cache Expirado

## Cenário 1 — Medição fresca

Com uma medição de menos de 30 min já em `medicoes_clima`, consultar `/clima/atual`.

**Esperado**: resposta imediata, `fonte_dados: "AO_VIVO"`, sem nova chamada externa.

## Cenário 2 — Medição expirada

Forçar uma medição com mais de 30 min e consultar.

**Esperado**: uma nova busca é disparada (verificável por log/mock) antes de responder.

## Cenário 3 — Concorrência

Disparar duas requisições simultâneas para o mesmo talhão com medição expirada.

**Esperado**: apenas uma busca externa é realizada; ambas as requisições recebem o resultado atualizado.

## Validação de sucesso

Feature validada quando os 3 cenários se comportam conforme descrito e os cabeçalhos no-cache estão sempre presentes.
