# Phase 0 Research: Estação INMET Mais Próxima

## Índice espacial para atingir <100ms

- **Decision**: índice GiST em `estacoes_inmet.posicao` (poucas dezenas de linhas, mas o índice garante performance estável mesmo com crescimento) e uso do operador `<->` (KNN — k-nearest-neighbor) do PostGIS, que já é otimizado para explorar esse índice.
- **Rationale**: é exatamente o padrão recomendado pela documentação do PostGIS para "nearest neighbor" — evita full scan calculando distância linha a linha.
- **Alternatives considered**: calcular distância haversine em Python para todas as estações e ordenar em memória — descartado explicitamente pelo Princípio IV da Constituição (nunca cálculo aproximado em aplicação) e mais lento que o índice nativo a partir de poucas dezenas de linhas.

## Unidade de distância retornada

- **Decision**: converter a distância geométrica (graus, em SRID 4326) para metros/quilômetros usando `ST_Distance` com `geography` (cast `::geography`) para obter distância real em metros, depois converter para km na resposta.
- **Rationale**: `<->` sobre `geometry` em SRID 4326 não retorna metros diretamente; o cast para `geography` garante que a distância retornada em km seja fisicamente correta, não apenas uma ordenação aproximada.
