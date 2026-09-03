# Feature Specification: Dashboard de Status de Plantio por Talhão

**Feature Branch**: `011-dashboard-status-plantio`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "HU-11: o produtor rural ou gestor visualiza um painel indicador de status de recomendação de plantio por talhão, para identificar rapidamente quais áreas estão prontas para plantar, quais requerem atenção e quais apresentam risco de perda."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver status de plantio de todos os talhões (Priority: P1)

O usuário abre um painel e vê, para cada talhão, se ele está pronto para plantio, em atenção, ou em risco.

**Why this priority**: é a forma consolidada de decisão de plantio para quem gerencia múltiplos talhões — sem isso, o usuário teria que avaliar cada talhão individualmente.

**Independent Test**: com talhões em diferentes níveis de umidade do solo, confirmar que cada um aparece classificado no status correto no painel.

**Acceptance Scenarios**:

1. **Given** um talhão com umidade adequada e chuva prevista suficiente, **When** o status é calculado, **Then** ele é classificado como **Verde (Ideal)**.
2. **Given** um talhão com umidade em nível crítico ou chuva excessiva prevista, **When** o status é calculado, **Then** ele é classificado como **Amarelo (Atenção)**.
3. **Given** um talhão com solo abaixo do ponto de murcha ou com excesso hídrico, **When** o status é calculado, **Then** ele é classificado como **Vermelho (Risco)**.

---

### User Story 2 - Filtrar o painel (Priority: P2)

O usuário filtra o painel por propriedade e por nível de status para focar em um subconjunto de talhões.

**Why this priority**: melhora a usabilidade em operações com muitos talhões, mas a visão geral (User Story 1) já entrega valor sem os filtros.

**Independent Test**: aplicar um filtro por propriedade e por status e confirmar que apenas os talhões correspondentes aparecem.

**Acceptance Scenarios**:

1. **Given** talhões de múltiplas propriedades e status, **When** o usuário filtra por uma propriedade específica, **Then** apenas os talhões dessa propriedade aparecem.
2. **Given** talhões em múltiplos status, **When** o usuário filtra por um nível de status, **Then** apenas os talhões nesse status aparecem.

---

### Edge Cases

- O que acontece com um talhão sem dados suficientes para calcular o Balanço Hídrico ainda (recém-cadastrado)? Ele aparece no painel com um estado neutro/pendente, nunca com uma das três cores de status válidas de forma enganosa.
- O que acontece quando não há nenhum talhão correspondente ao filtro aplicado? O painel exibe claramente que não há resultados, em vez de aparentar estar vazio por erro.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST exibir, para cada talhão, um indicador de status de plantio entre Verde, Amarelo ou Vermelho, calculado a partir do Balanço Hídrico do Solo.
- **FR-002**: O sistema MUST classificar como **Verde** um talhão com umidade da camada 0-7cm adequada para germinação e previsão de chuva regular acumulada.
- **FR-003**: O sistema MUST classificar como **Amarelo** um talhão com umidade em limite crítico ou previsão de chuva excessiva nas próximas 24 horas.
- **FR-004**: O sistema MUST classificar como **Vermelho** um talhão com solo abaixo do Ponto de Murcha Permanente ou em excesso hídrico que impeça tráfego de maquinário.
- **FR-005**: O sistema MUST permitir filtrar os talhões exibidos por propriedade e por nível de status.

### Key Entities

- **Status de Plantio**: um entre três estados mutuamente exclusivos (Verde, Amarelo, Vermelho) associado a um talhão, derivado do Balanço Hídrico do Solo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um usuário identifica quantos e quais talhões estão em risco (Vermelho) em menos de 5 segundos ao abrir o painel.
- **SC-002**: 100% dos talhões com Balanço Hídrico calculado exibem um status entre as três categorias válidas, sem ambiguidade.
- **SC-003**: Um filtro por propriedade ou status reduz a lista exibida ao subconjunto correto em 100% dos casos testados.

## Assumptions

- Esta feature consome o armazenamento de água calculado pela feature de Balanço Hídrico do Solo (010); não recalcula o balanço por conta própria.
- Os limiares exatos de percentual de CAD para cada faixa de status seguem a matriz oficial já definida em `requisitos/REQUISITOS.md` (RN004-RN006).
