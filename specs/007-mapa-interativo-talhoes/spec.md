# Feature Specification: Visualização Interativa de Talhões e Estações em Mapa

**Feature Branch**: `007-mapa-interativo-talhoes`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "HU-07: o usuário visualiza em um mapa interativo os limites das propriedades, a divisão dos talhões e os pontos das estações meteorológicas do Pará, para que a navegação e a tomada de decisão no campo sejam visuais e intuitivas."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver propriedades, talhões e estações no mapa (Priority: P1)

O usuário abre o mapa e vê visualmente onde estão suas propriedades, como os talhões estão divididos, e onde ficam as estações meteorológicas próximas.

**Why this priority**: é a forma primária pela qual o usuário entende espacialmente seus dados — sem essa visão, ele depende de listas de texto para uma decisão que é inerentemente geográfica.

**Independent Test**: abrir o mapa com pelo menos uma propriedade cadastrada e confirmar visualmente a presença dos polígonos de talhão e dos pontos de estação.

**Acceptance Scenarios**:

1. **Given** propriedades e talhões cadastrados, **When** o usuário abre o mapa, **Then** vê os polígonos dos talhões e os pontos das estações meteorológicas próximas sobre uma camada de satélite.
2. **Given** o mapa aberto em um celular ou em um desktop, **When** o usuário interage com ele (zoom, arraste), **Then** a experiência permanece utilizável em ambos os tamanhos de tela.

---

### User Story 2 - Cor do talhão reflete o status de plantio (Priority: P1)

Cada talhão no mapa é colorido de acordo com seu status atual de plantio, para leitura rápida sem precisar abrir detalhes.

**Why this priority**: é o que transforma o mapa de "visualização geográfica" em "ferramenta de decisão" — o usuário identifica riscos de relance.

**Independent Test**: com talhões em diferentes status, confirmar que cada um aparece na cor correspondente (Verde/Amarelo/Vermelho).

**Acceptance Scenarios**:

1. **Given** um talhão com status de plantio Verde, Amarelo ou Vermelho, **When** ele é exibido no mapa, **Then** sua cor no mapa corresponde exatamente a esse status.

---

### User Story 3 - Ver detalhes ao clicar (Priority: P2)

Ao clicar em um talhão ou estação, o usuário vê um resumo das últimas medições sem sair do mapa.

**Why this priority**: complementa a visão geral (User Stories 1-2) com o detalhe necessário para agir, mas o mapa já entrega valor sem esse detalhe imediato.

**Independent Test**: clicar em um talhão ou estação no mapa e confirmar que um popup com as últimas medições aparece.

**Acceptance Scenarios**:

1. **Given** um talhão ou estação no mapa, **When** o usuário clica sobre ele, **Then** um popup/card exibe as últimas medições de chuva, vento e umidade do solo daquele ponto.

---

### Edge Cases

- O que acontece quando um talhão ainda não tem status de plantio calculado (recém-cadastrado)? Ele deve ser exibido com uma cor/estado neutro, nunca com uma das três cores de status válidas de forma enganosa.
- O que acontece com dezenas ou centenas de talhões próximos uns dos outros? O mapa deve permanecer utilizável (zoom/agrupamento visual), sem detalhamento aprofundado nesta spec.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST exibir em um mapa interativo os polígonos das propriedades e talhões cadastrados e os pontos das estações meteorológicas do Pará, sobre uma camada de imagem de satélite.
- **FR-002**: O sistema MUST colorir cada talhão no mapa de acordo com seu status de plantio atual (Verde, Amarelo ou Vermelho).
- **FR-003**: O sistema MUST exibir um popup/card com as últimas medições de chuva, vento e umidade do solo ao selecionar um talhão ou uma estação no mapa.
- **FR-004**: O componente de mapa MUST ser utilizável tanto em dispositivos móveis quanto em desktop.

### Key Entities

- **Camada de Mapa**: representação visual de propriedades, talhões (coloridos por status) e estações sobre uma base de satélite.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um usuário identifica visualmente o status de qualquer talhão visível no mapa em menos de 3 segundos, sem precisar abrir detalhes.
- **SC-002**: O mapa carrega e se torna interativo em menos de 3 segundos em conexão móvel típica.
- **SC-003**: 100% dos talhões com status calculado exibem a cor correspondente correta.

## Assumptions

- Esta feature consome dados já calculados pelas features de Dashboard de Plantio (HU-11) e Motor de Pulverização (HU-09); não recalcula status por conta própria.
- A biblioteca de mapa e a camada de satélite específicas são detalhes de implementação a definir no plano técnico.
