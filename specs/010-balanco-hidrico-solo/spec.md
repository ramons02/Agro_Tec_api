# Feature Specification: Algoritmo de Balanço Hídrico do Solo para Janela de Plantio

**Feature Branch**: `010-balanco-hidrico-solo`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "HU-10: o sistema calcula a estimativa de umidade e armazenamento de água no solo do talhão, para que seja determinada a janela ideal de semeadura/plantio de acordo com a disponibilidade hídrica."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Calcular o armazenamento diário de água no solo (Priority: P1)

O sistema calcula, dia a dia, quanta água está armazenada no solo de um talhão, combinando o armazenamento do dia anterior, a chuva e a evapotranspiração.

**Why this priority**: é o cálculo central do qual dependem o Dashboard de Plantio (HU-11) e a Recomendação Acionável (HU-12) — sem ele, nenhuma decisão de janela de plantio pode ser feita.

**Independent Test**: fornecer o armazenamento do dia anterior, a precipitação e a evapotranspiração do dia, e confirmar que o novo armazenamento calculado corresponde exatamente à fórmula oficial, respeitando os limites mínimo (0) e máximo (capacidade do solo).

**Acceptance Scenarios**:

1. **Given** o armazenamento de água do dia anterior, a precipitação e a evapotranspiração do dia, **When** o sistema calcula o balanço, **Then** o novo armazenamento nunca é negativo nem ultrapassa a Capacidade de Água Disponível do talhão.
2. **Given** um talhão com seu perfil de solo (CAD) já definido, **When** o cálculo diário é executado, **Then** ele usa a CAD específica daquele talhão, não um valor genérico.

---

### Edge Cases

- O que acontece no primeiro dia de cálculo de um talhão recém-cadastrado, sem armazenamento anterior? Um valor inicial razoável de armazenamento deve ser assumido (ver Assumptions).
- O que acontece se a evapotranspiração do dia for maior que a água disponível no solo? O armazenamento resultante é zero, nunca negativo.
- O que acontece se a chuva do dia for muito acima do normal? O armazenamento resultante é limitado à Capacidade de Água Disponível, nunca ultrapassando-a.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST calcular diariamente o armazenamento de água no solo de cada talhão, combinando o armazenamento do dia anterior, a precipitação do dia e a evapotranspiração do dia.
- **FR-002**: O sistema MUST limitar o armazenamento calculado entre zero e a Capacidade de Água Disponível (CAD) específica do talhão.
- **FR-003**: O sistema MUST considerar a profundidade da camada de germinação (0-7cm) e as propriedades físicas do solo do talhão (obtidas na feature de Solo) no cálculo.
- **FR-004**: O sistema MUST tornar o resultado do cálculo diário disponível para consumo pelas features de Dashboard de Plantio e Recomendação Acionável.

### Key Entities

- **Armazenamento de Água no Solo (diário)**: valor calculado por talhão e por dia, limitado entre zero e a CAD do talhão.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% dos cálculos de armazenamento diário respeitam os limites mínimo e máximo definidos pela CAD do talhão, verificável por teste automatizado.
- **SC-002**: O armazenamento de água de todos os talhões ativos é recalculado dentro de 24 horas após a chegada de novos dados de precipitação/evapotranspiração.

## Assumptions

- No primeiro dia de um talhão sem histórico, o armazenamento inicial assume um valor de referência razoável (ex.: um percentual médio da CAD) — o valor exato é um detalhe de calibração a ser definido no plano técnico.
- O coeficiente de cultivo ($K_c$) usado para derivar a evapotranspiração da cultura a partir da evapotranspiração de referência ($ET_0$) corresponde à fase inicial da cultura, conforme `requisitos/REQUISITOS.md` (RN007).
- Depende das features de Solo (004, para CAD) e Previsão Open-Meteo (003, para ET0 e precipitação) como fontes de dado.
