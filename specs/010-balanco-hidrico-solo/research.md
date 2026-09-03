# Phase 0 Research: Balanço Hídrico do Solo

## Valor inicial de armazenamento

- **Decision**: para um talhão sem histórico (primeiro dia), assumir $ARM_0 = 0,70 \times CAD$ (aproximadamente o limite superior da faixa Verde/Amarelo, um ponto de partida neutro-otimista).
- **Rationale**: evita que todo talhão recém-cadastrado comece artificialmente em risco (Vermelho) ou artificialmente ideal (no teto da CAD); é uma assunção documentada na spec, sujeita a recalibração.
- **Alternatives considered**: começar em 0 (pior caso) ou na CAD cheia (melhor caso) — ambos enviesam a primeira recomendação do produto sem base real.

## Coeficiente de cultivo ($K_c$)

- **Decision**: usar um valor fixo de $K_c$ da fase inicial de desenvolvimento (ex.: 0,4, valor típico de referência FAO-56 para fase inicial de culturas anuais) até que o produto suporte seleção de cultura por talhão.
- **Rationale**: RN007 já define $ET_i = ET_0 \times K_c$ sem especificar cultura — um valor único e documentado é a decisão de menor escopo consistente com o MVP.
- **Alternatives considered**: pedir ao usuário a cultura e uma tabela de $K_c$ por fase — funcionalidade maior, fora do escopo desta feature/MVP.

## Execução do job diário

- **Decision**: reaproveitar o mesmo agendador (APScheduler) já registrado pela feature 002, adicionando um job separado agendado uma vez por dia (ex.: logo após a meia-noite UTC).
- **Rationale**: evita introduzir um segundo mecanismo de agendamento.
