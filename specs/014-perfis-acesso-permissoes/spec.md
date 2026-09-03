# Feature Specification: Perfis de Acesso e Permissões

**Feature Branch**: `014-perfis-acesso-permissoes`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "HU-14 (validada com o dono do produto em 2026-09-03): cada papel de usuário (produtor rural, agrônomo, gestor de tecnologia) tem um nível de acesso adequado, para que um agrônomo não edite acidentalmente uma propriedade que não é dele e um produtor não veja dados de terceiros."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Produtor gerencia só suas próprias propriedades (Priority: P1)

Um produtor rural só consegue criar, editar e excluir propriedades e talhões dos quais é o dono cadastrado.

**Why this priority**: é a garantia básica de isolamento de dados entre produtores diferentes — sem ela, qualquer usuário poderia alterar dados de qualquer propriedade.

**Independent Test**: com dois produtores e suas respectivas propriedades, confirmar que um não consegue editar ou excluir propriedade do outro.

**Acceptance Scenarios**:

1. **Given** um produtor autenticado, **When** ele tenta editar ou excluir uma propriedade da qual é o dono, **Then** a ação é permitida.
2. **Given** um produtor autenticado, **When** ele tenta editar ou excluir uma propriedade da qual não é o dono, **Then** a ação é bloqueada com um erro de permissão explícito (não um erro de "não encontrado").

---

### User Story 2 - Agrônomo tem acesso somente leitura a propriedades vinculadas (Priority: P1)

Um agrônomo só visualiza propriedades às quais foi explicitamente vinculado por um produtor, e nunca pode editar ou excluir.

**Why this priority**: é o caso de uso central de consultoria externa — sem essa restrição, um agrônomo teria acesso indevido a dados de propriedades não relacionadas a ele.

**Independent Test**: vincular um agrônomo a uma propriedade específica e confirmar que ele só a enxerga (não outras) e não consegue nenhuma ação de escrita nela.

**Acceptance Scenarios**:

1. **Given** um agrônomo vinculado a uma propriedade, **When** ele consulta suas propriedades, **Then** vê apenas as propriedades às quais foi vinculado, nunca as demais do sistema.
2. **Given** um agrônomo vinculado a uma propriedade, **When** ele tenta criar, editar ou excluir um talhão dela, **Then** a ação é bloqueada com um erro de permissão explícito.

---

### User Story 3 - Vínculo agrônomo-propriedade exige aceite (Priority: P2)

Um convite de vínculo entre agrônomo e propriedade só passa a valer depois que o agrônomo o aceita.

**Why this priority**: evita que um produtor conceda acesso a um agrônomo sem o conhecimento/consentimento dele; complementa a User Story 2, mas o modelo de acesso somente leitura já faz sentido sem esse fluxo de convite.

**Independent Test**: criar um convite de vínculo e confirmar que o agrônomo não tem acesso à propriedade até aceitar explicitamente o convite.

**Acceptance Scenarios**:

1. **Given** um convite de vínculo enviado a um agrônomo, **When** ele ainda não foi aceito, **Then** o agrônomo não tem nenhum acesso à propriedade.
2. **Given** um convite de vínculo, **When** o agrônomo o aceita, **Then** ele passa a ter acesso de leitura àquela propriedade.

---

### User Story 4 - Gestor de tecnologia tem acesso total de suporte (Priority: P2)

Um gestor de tecnologia acessa e edita qualquer propriedade do sistema para fins de suporte/administração.

**Why this priority**: necessário para operação e suporte da plataforma, mas é um papel administrativo interno, não o fluxo principal do produto para o cliente final.

**Independent Test**: com um gestor de tecnologia autenticado, confirmar acesso de leitura e escrita a uma propriedade de qualquer produtor.

**Acceptance Scenarios**:

1. **Given** um gestor de tecnologia autenticado, **When** ele acessa qualquer propriedade do sistema, **Then** tem permissão de leitura e escrita sobre ela.

---

### Edge Cases

- O que acontece quando uma ação de escrita é bloqueada por permissão? O sistema retorna um erro explicando o motivo do bloqueio, nunca um erro genérico de "recurso não encontrado" (que esconderia a existência do recurso e confundiria o usuário).
- O que acontece se um vínculo agrônomo-propriedade for revogado pelo produtor? O agrônomo perde o acesso de leitura imediatamente.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST reconhecer exatamente três papéis de usuário: `PRODUTOR_RURAL`, `AGRONOMO` e `GESTOR_TECNOLOGIA`.
- **FR-002**: O sistema MUST permitir que um `PRODUTOR_RURAL` crie, edite e exclua apenas propriedades e talhões dos quais é o dono cadastrado.
- **FR-003**: O sistema MUST conceder a um `GESTOR_TECNOLOGIA` acesso de leitura e escrita a todas as propriedades do sistema.
- **FR-004**: O sistema MUST restringir um `AGRONOMO` a acesso somente leitura, e apenas às propriedades às quais foi explicitamente vinculado.
- **FR-005**: O sistema MUST exigir que um vínculo entre agrônomo e propriedade seja aceito explicitamente pelo agrônomo antes de conceder qualquer acesso (nunca vínculo unilateral).
- **FR-006**: O sistema MUST retornar um erro de permissão explícito (não um erro de recurso inexistente) para toda ação de escrita bloqueada por falta de permissão.
- **FR-007**: O sistema MUST revogar o acesso de leitura de um agrônomo a uma propriedade imediatamente quando o vínculo for desfeito.

### Key Entities

- **Papel de Usuário**: um entre três valores mutuamente exclusivos (`PRODUTOR_RURAL`, `AGRONOMO`, `GESTOR_TECNOLOGIA`) associado a uma conta.
- **Vínculo Agrônomo-Propriedade**: relação entre um agrônomo e uma propriedade, com estado (convidado/aceito) que determina se o acesso de leitura está ativo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% das tentativas de um produtor editar propriedade de outro produtor são bloqueadas, verificável por teste automatizado.
- **SC-002**: 100% das tentativas de escrita por um agrônomo são bloqueadas, independentemente de vínculo.
- **SC-003**: Um agrônomo nunca enxerga, em nenhuma listagem, uma propriedade à qual não está vinculado e aceito.

## Assumptions

- O fluxo de convite/aceite (User Story 3) é uma novidade em relação ao protótipo navegável, que simula apenas a restrição de leitura sem convite real — esta spec formaliza o comportamento completo esperado no backend real.
- Depende da feature de Cadastro de Conta (013) para a existência de contas com papel definido, e da feature de Cadastro Territorial (005) para propriedades/talhões existirem.
