# Feature Specification: Parametrização Automática de Solo via SoilGrids

**Feature Branch**: `004-solo-soilgrids`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "HU-04: ao cadastrar um talhão, a textura do solo e a Capacidade de Retenção de Água são preenchidas automaticamente a partir das coordenadas geográficas, sem exigir análise de laboratório manual."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Classificação automática de solo no cadastro (Priority: P1)

Ao informar a coordenada central de um talhão, o produtor recebe automaticamente a classificação de tipo de solo, sem precisar de análise de laboratório.

**Why this priority**: é o valor central da feature — elimina uma barreira de entrada (custo/tempo de análise laboratorial) que impediria o cadastro completo do talhão.

**Independent Test**: informar uma coordenada válida no Pará e confirmar que o tipo de solo retorna classificado automaticamente.

**Acceptance Scenarios**:

1. **Given** a coordenada central de um talhão, **When** o sistema consulta a fonte de dados de solo, **Then** extrai as frações de argila, areia, silte e matéria orgânica daquele ponto.
2. **Given** as frações extraídas, **When** o sistema as processa, **Then** classifica o talhão como um entre `ARGILOSO`, `ARENOSO` ou `MISTO` e grava essa classificação vinculada ao talhão.

---

### User Story 2 - Cálculo da Capacidade de Água Disponível (Priority: P1)

O sistema calcula e armazena a Capacidade de Água Disponível (CAD) do talhão a partir dos dados de solo obtidos.

**Why this priority**: a CAD é o insumo direto do Balanço Hídrico do Solo (HU-10) — sem ela, não há como determinar a janela de plantio.

**Independent Test**: com as frações de solo já obtidas, confirmar que um valor de CAD é calculado e persistido para o talhão.

**Acceptance Scenarios**:

1. **Given** as propriedades físicas do solo obtidas para um talhão, **When** o sistema calcula a CAD, **Then** o valor é persistido e associado ao talhão para uso posterior no Balanço Hídrico.

---

### Edge Cases

- O que acontece se a fonte de dados de solo não tiver cobertura para a coordenada informada? O talhão deve ser aceito mesmo assim, com o tipo de solo marcado como pendente/indisponível, nunca bloqueando o cadastro.
- O que acontece se a classificação retornar valores no limite entre duas categorias (ex.: quase igual entre argiloso e misto)? Aplica-se a regra de classificação oficial sem margem de ambiguidade adicional — a fronteira exata é um detalhe de implementação a documentar no plano técnico.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST consultar a fonte de dados de solo pela coordenada central do talhão ao cadastrá-lo.
- **FR-002**: O sistema MUST extrair frações de argila, areia, silte e matéria orgânica para a coordenada consultada.
- **FR-003**: O sistema MUST classificar automaticamente o tipo de solo do talhão como `ARGILOSO`, `ARENOSO` ou `MISTO`.
- **FR-004**: O sistema MUST calcular e persistir a Capacidade de Água Disponível (CAD) do talhão a partir dos dados de solo obtidos.
- **FR-005**: O sistema MUST operar sem custo de bilhetagem nesta integração.
- **FR-006**: O sistema MUST permitir a conclusão do cadastro do talhão mesmo quando a fonte de dados de solo não tiver cobertura para a coordenada informada.

### Key Entities

- **Perfil de Solo do Talhão**: frações de argila/areia/silte/matéria orgânica, tipo de solo classificado e Capacidade de Água Disponível, associados a um talhão.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um talhão recém-cadastrado com coordenada válida no Pará recebe classificação de solo e CAD calculados em menos de 5 segundos, sem intervenção manual.
- **SC-002**: 100% dos talhões cadastrados com coordenada coberta pela fonte de dados recebem um tipo de solo entre as três categorias válidas.
- **SC-003**: Zero custo de bilhetagem gerado por esta integração.

## Assumptions

- A fórmula oficial de cálculo da CAD ($CAD = (CC - PMP) \times \rho_s \times z$) e os limiares de classificação de textura já estão definidos em `escopo/calculos-geo-metero.md` e no `requisitos/REQUISITOS.md` (RN020) — esta spec não redefine a fórmula, apenas o comportamento do sistema ao aplicá-la.
- A profundidade de raízes ($z$) usada no cálculo assume um valor padrão razoável para a fase inicial da cultura quando não informado explicitamente pelo usuário.
