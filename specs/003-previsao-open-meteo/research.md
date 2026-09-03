# Phase 0 Research: Integração com Open-Meteo

## Estratégia para respeitar o limite de 10.000 chamadas/dia

- **Decision**: cache em memória (ou Redis, se disponível) por chave `(lat arredondada, long arredondada, hora)`, com TTL de 30 minutos — alinhado ao limiar de staleness da feature 008.
- **Rationale**: talhões próximos compartilham essencialmente a mesma previsão; arredondar coordenada evita uma chamada por talhão quando vários estão próximos, reduzindo drasticamente o volume de chamadas.
- **Alternatives considered**: sem cache (uma chamada por talhão a cada consulta) — inviável a partir de poucas centenas de talhões com consultas frequentes; excederia o limite gratuito.

## Formato de resposta da Open-Meteo

- **Decision**: usar o endpoint de previsão horária com parâmetros `wind_speed_10m`, `wind_speed_100m`, `et0_fao_evapotranspiration`, `soil_moisture_0_to_7cm` (e demais camadas), conforme documentação pública da API.
- **Rationale**: são exatamente os parâmetros citados na Constituição do Projeto e no Escopo V2.

## Timeout e resiliência

- **Decision**: timeout de 3s por chamada (mesmo padrão da feature 002), sem retry automático dentro do orçamento de 2s de resposta ao usuário.
- **Rationale**: consistência com o padrão de resiliência já adotado para o INMET.
