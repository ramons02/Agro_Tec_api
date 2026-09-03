# Feature Specification: Integração com Open-Meteo para Previsão Climática e Solo

**Feature Branch**: `003-previsao-open-meteo`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "HU-03: o sistema integra com a API gratuita do Open-Meteo para obter previsão horária de vento (10m e 100m), evapotranspiração (ET0) e umidade do solo em 4 profundidades, alimentando o Balanço Hídrico."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Obter previsão para um talhão específico (Priority: P1)

O sistema consulta a previsão climática e de solo para a coordenada exata de um talhão, para uso nos cálculos de pulverização e plantio.

**Why this priority**: sem esse dado de previsão, os motores de pulverização (HU-09) e de Balanço Hídrico (HU-10) não têm insumo para funcionar.

**Independent Test**: solicitar a previsão para uma coordenada válida e confirmar que vento, evapotranspiração e umidade do solo em 4 profundidades retornam estruturados.

**Acceptance Scenarios**:

1. **Given** a latitude e longitude de um talhão, **When** o sistema consulta a previsão, **Then** recebe vento horário (10m e 100m), evapotranspiração diária e umidade do solo em 4 profundidades.
2. **Given** os dados de umidade do solo (0-7cm) e evapotranspiração recebidos, **When** eles são processados, **Then** ficam disponíveis em formato pronto para o cálculo do Balanço Hídrico do Solo.

---

### User Story 2 - Operar dentro do limite gratuito (Priority: P2)

O sistema respeita o limite de requisições gratuitas do provedor, evitando qualquer cobrança.

**Why this priority**: é uma restrição não-negociável de custo do projeto (orçamento $0,00 para APIs de terceiros).

**Independent Test**: medir o volume de chamadas geradas em um dia de uso típico e confirmar que fica abaixo do limite gratuito documentado.

**Acceptance Scenarios**:

1. **Given** o volume normal de talhões cadastrados, **When** o sistema realiza consultas de previsão ao longo do dia, **Then** o total de chamadas permanece abaixo de 10.000 por dia.

---

### Edge Cases

- O que acontece quando a previsão para uma coordenada não está disponível (fora de cobertura)? O sistema deve tratar como falha de fonte e seguir o comportamento de fallback definido na feature de Ingestão INMET/RN017.
- O que acontece se o limite diário gratuito estiver perto de ser atingido? Fora de escopo desta feature definir uma política de rate-limiting proativa — ver Assumptions.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST consultar a previsão climática e de solo informando a latitude e longitude exatas do talhão.
- **FR-002**: O sistema MUST obter, para cada consulta, vento horário em 10m e 100m, evapotranspiração diária (ET0) e umidade do solo em 4 profundidades (0-7cm, 7-28cm e demais camadas fornecidas pela fonte).
- **FR-003**: O sistema MUST estruturar a umidade do solo (0-7cm) e a evapotranspiração diária em formato consumível pelo cálculo do Balanço Hídrico do Solo.
- **FR-004**: O sistema MUST operar dentro do limite gratuito de requisições do provedor (até 10.000 chamadas/dia), sem exigir chave paga.

### Key Entities

- **Previsão Climática**: conjunto de vento (10m/100m) e evapotranspiração previstos para uma coordenada e um horizonte de tempo.
- **Umidade do Solo por Profundidade**: percentual de umidade em cada uma das 4 camadas de profundidade do solo, para uma coordenada.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A previsão de vento, evapotranspiração e umidade do solo para qualquer talhão cadastrado no Pará fica disponível em menos de 2 segundos por consulta.
- **SC-002**: O volume de chamadas diárias à fonte de previsão nunca ultrapassa o limite gratuito documentado.
- **SC-003**: Zero custo de bilhetagem gerado por esta integração, verificável em qualquer período de operação.

## Assumptions

- Uma política de cache/agendamento para conter o volume de chamadas é um detalhe de implementação a ser definido no plano técnico, não uma decisão de produto desta spec.
- A cobertura geográfica do provedor de previsão inclui integralmente o estado do Pará (premissa da Constituição do Projeto).
