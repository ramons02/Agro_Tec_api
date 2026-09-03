# Phase 0 Research: Dashboard de Status de Plantio

## Calcular on-the-fly vs. persistir status

- **Decision**: persistir o status classificado junto ao registro diário de `balanco_hidrico_diario` (coluna `status_plantio`), calculado no mesmo job que grava o armazenamento (feature 010).
- **Rationale**: evita recalcular a classificação a cada leitura do dashboard e mantém a lógica de classificação executada em um único lugar; a listagem do dashboard só filtra/pagina.
- **Alternatives considered**: calcular a classificação em tempo de leitura no endpoint — mais simples de implementar, mas duplicaria trabalho de CPU a cada requisição sem necessidade, já que o valor só muda uma vez por dia.

## Regra de classificação (fronteiras)

- **Decision**: reaproveitar exatamente os limiares e a regra de fallback conservador já validados em `requisitos/REQUISITOS.md` (RN004-RN006): Verde 60-90% CAD + chuva prevista ≥5mm; Amarelo cobre as faixas 90-95% e 60-90% sem os 5mm, além de 30-60%; Vermelho <30% ou >95%.
- **Rationale**: já é a regra oficial validada; nenhuma reinterpretação necessária.
