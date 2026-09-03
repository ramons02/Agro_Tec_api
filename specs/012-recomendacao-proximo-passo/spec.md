# Feature Specification: Recomendação Acionável de "Próximo Passo" por Talhão

**Feature Branch**: `012-recomendacao-proximo-passo`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "HU-12 (validada com o dono do produto em 2026-09-03): o produtor rural ou agrônomo vê uma recomendação única combinando o status de plantio e o status de pulverização daquele momento, para não precisar cruzar mentalmente dois indicadores separados."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver uma recomendação única combinando os dois sinais (Priority: P1)

Ao olhar o painel de um talhão, o usuário vê uma frase curta dizendo o que fazer agora, combinando status de plantio e de pulverização.

**Why this priority**: é o valor central desta feature — resolve a lacuna de ter dois indicadores que o usuário precisava cruzar mentalmente.

**Independent Test**: com um status de plantio e um status de pulverização conhecidos para um talhão, confirmar que a recomendação exibida reflete corretamente a combinação dos dois.

**Acceptance Scenarios**:

1. **Given** o status de plantio e o status de pulverização de um talhão, **When** o usuário abre o painel de detalhe, **Then** vê uma seção com uma recomendação de próximo passo combinando os dois sinais.

---

### User Story 2 - Priorização clara da recomendação (Priority: P1)

A recomendação vem acompanhada de um nível de prioridade (Alta, Média ou Baixa), sinalizado por cor e por texto.

**Why this priority**: sem prioridade, o usuário não sabe quais dos vários talhões merecem atenção imediata — é o que torna a recomendação acionável, não apenas informativa.

**Independent Test**: com talhões em diferentes combinações de status, confirmar que a prioridade atribuída segue a regra esperada e nunca depende só de cor para ser identificada.

**Acceptance Scenarios**:

1. **Given** um talhão com status de plantio Vermelho, **When** a recomendação é gerada, **Then** a prioridade é **Alta**, independentemente do status de pulverização.
2. **Given** um talhão com status de plantio Amarelo ou pulverização bloqueada, **When** a recomendação é gerada, **Then** a prioridade é **Média**.
3. **Given** qualquer recomendação exibida, **When** o usuário a vê, **Then** a prioridade é identificável tanto pela cor quanto por um texto (label), nunca só pela cor.

---

### User Story 3 - Considerar tendência de umidade em status Amarelo (Priority: P2)

Quando o status de plantio é Amarelo, a recomendação diferencia se a situação está melhorando ou piorando, em vez de olhar só o valor pontual.

**Why this priority**: refina a qualidade da recomendação em um caso ambíguo (Amarelo pode estar melhorando ou piorando), mas as User Stories 1-2 já entregam o valor central sem esse refinamento.

**Independent Test**: com um talhão em status Amarelo e uma tendência de umidade conhecida (subindo, caindo ou estável), confirmar que a recomendação reflete essa tendência.

**Acceptance Scenarios**:

1. **Given** um talhão em status Amarelo com umidade subindo nos últimos dias, **When** a recomendação é gerada, **Then** o texto indica melhora, distinto do texto usado quando a umidade está caindo.

---

### Edge Cases

- O que acontece se o status de pulverização não puder ser calculado (sem estação/dado disponível)? A recomendação deve informar essa limitação em vez de assumir um status de pulverização.
- Toda recomendação exibida deve trazer um aviso de que é uma sugestão automática e não substitui avaliação agronômica profissional.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST gerar uma recomendação de próximo passo combinando o status de plantio e o status de pulverização mais recente de um talhão.
- **FR-002**: O sistema MUST atribuir prioridade **Alta** quando o status de plantio for Vermelho, independentemente do status de pulverização.
- **FR-003**: O sistema MUST atribuir prioridade **Média** quando o status de plantio for Amarelo, ou quando a pulverização estiver bloqueada (vento forte ou inversão térmica).
- **FR-004**: O sistema MUST exibir a prioridade sempre com cor e com texto (label), nunca apenas por cor.
- **FR-005**: Quando o status de plantio for Amarelo, o sistema MUST considerar a tendência de umidade do solo nos últimos 3 dias (subindo, caindo ou estável) ao formular o texto da recomendação.
- **FR-006**: O sistema MUST classificar a tendência como "subindo" ou "caindo" quando a variação de umidade nos últimos 3 dias for de pelo menos 1,5 pontos percentuais; abaixo disso, a tendência é "estável".
- **FR-007**: O sistema MUST exibir, junto a toda recomendação, um aviso de que é uma sugestão automática que não substitui avaliação agronômica profissional.
- **FR-008**: A lógica de geração da recomendação MUST ser isolável e auditável (sem efeitos colaterais), para permitir ajuste conforme o negócio validar ou corrigir as regras.

### Key Entities

- **Recomendação de Próximo Passo**: texto curto e nível de prioridade (Alta/Média/Baixa) gerados a partir do status de plantio e do status de pulverização de um talhão em um dado momento.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% das recomendações com status de plantio Vermelho recebem prioridade Alta, verificável por teste automatizado.
- **SC-002**: Um usuário identifica a prioridade de uma recomendação sem depender de percepção de cor (ex.: daltonismo), em 100% das telas.
- **SC-003**: Um usuário entende o que fazer a seguir para um talhão em menos de 10 segundos ao abrir seu painel de detalhe.

## Assumptions

- O limiar de tendência de umidade (1,5 pontos percentuais em 3 dias) é uma decisão de sensibilidade de UX validada com o dono do produto em 2026-09-03 (RN019), não uma medida científica — sujeita a recalibração quando o Balanço Hídrico operar com dados reais.
- Depende das features de Dashboard de Plantio (011, para status de plantio) e Motor de Pulverização (009, para status de pulverização).
