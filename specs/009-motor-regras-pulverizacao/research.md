# Phase 0 Research: Motor de Regras de Pulverização

Nenhuma pesquisa de tecnologia necessária — a regra de negócio já está integralmente fechada e validada (RN001-RN003, superando a antiga RN014). Este documento apenas confirma a portabilidade da implementação de referência.

## Portar do protótipo

- **Decision**: portar `regrasPulverizacao.ts` (TypeScript) para uma função Python equivalente e pura, mantendo exatamente os mesmos limiares (3, 10, 15 km/h) e a mesma precedência de regras (inversão térmica é checada isoladamente por vento < 3km/h, sem depender de mais nada).
- **Rationale**: a lógica já foi validada com o dono do produto no protótipo; reescrever do zero arriscaria divergência sutil.
- **Alternatives considered**: motor de regras genérico (rule engine) — descartado por ser complexidade desnecessária para 3 regras fixas e simples (YAGNI).
