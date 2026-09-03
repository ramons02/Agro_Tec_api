# Phase 0 Research: Recomendação Acionável de "Próximo Passo"

## Cálculo de tendência de umidade

- **Decision**: comparar o `armazenamento_mm` (como percentual da CAD) do dia atual com o de 3 dias atrás; diferença ≥1,5 p.p. classifica como "subindo"/"caindo" (conforme o sinal), abaixo disso "estável" (RN019).
- **Rationale**: é exatamente a regra validada com o dono do produto; nenhum cálculo científico adicional é necessário.
- **Alternatives considered**: regressão linear sobre os últimos N dias — precisão maior, mas complexidade desnecessária para um limiar de UX já validado como simples diferença de 3 pontos.

## Texto da recomendação

- **Decision**: templates de texto por combinação (status de plantio × status de pulverização × tendência quando Amarelo), similar ao `recomendacao.ts` do protótipo, não geração de linguagem natural livre.
- **Rationale**: mantém a recomendação auditável e previsível (FR-008) — texto gerado por regra determinística, não por um modelo probabilístico.
