# Feature Specification: Cadastro de Conta e Recuperação de Senha

**Feature Branch**: `013-cadastro-conta-recuperacao-senha`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "HU-13 (validada com o dono do produto em 2026-09-03): um produtor rural, agrônomo ou gestor de tecnologia sem conta se cadastra informando nome, email, senha e papel, e pode recuperar a senha se esquecer."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Criar conta nova (Priority: P1)

Uma pessoa sem conta se cadastra informando nome, email, senha e seu papel, e passa a poder fazer login.

**Why this priority**: sem essa feature, ninguém consegue existir como usuário do sistema de forma independente — é pré-requisito para qualquer uso real da plataforma.

**Independent Test**: cadastrar uma conta nova com dados válidos e confirmar que o login (feature 001) passa a funcionar com essas credenciais.

**Acceptance Scenarios**:

1. **Given** nome, email, senha e papel válidos e não usados antes, **When** a pessoa se cadastra, **Then** uma conta é criada e ela pode fazer login em seguida.
2. **Given** um email já cadastrado, **When** alguém tenta se cadastrar com ele novamente, **Then** o cadastro é recusado informando que o email já está em uso.
3. **Given** uma senha com menos de 8 caracteres, **When** o cadastro é tentado, **Then** ele é recusado por senha muito curta.

---

### User Story 2 - Recuperar senha esquecida (Priority: P1)

Um usuário que esqueceu a senha solicita redefinição e recebe um link por email com prazo limitado.

**Why this priority**: sem essa feature, esquecer a senha significaria perder acesso permanente à conta — inaceitável para um produto de uso contínuo no campo.

**Independent Test**: solicitar recuperação de senha para um email cadastrado e confirmar que um link de redefinição com expiração é enviado e funciona uma única vez dentro do prazo.

**Acceptance Scenarios**:

1. **Given** um email cadastrado, **When** o usuário solicita recuperação de senha, **Then** um link de redefinição com token de expiração de 1 hora é enviado a esse email.
2. **Given** um token de redefinição já usado ou expirado, **When** o usuário tenta usá-lo novamente, **Then** o sistema rejeita com uma mensagem clara, nunca um erro genérico.

---

### Edge Cases

- O que acontece se alguém solicitar recuperação de senha para um email que não existe no sistema? A resposta não deve revelar se o email existe ou não, para não vazar quais emails têm conta.
- O que acontece se o usuário definir uma nova senha idêntica à anterior durante a recuperação? Aceito — não há requisito de diferença obrigatória nesta feature.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST permitir que uma pessoa sem conta se cadastre informando nome, email, senha e papel (`PRODUTOR_RURAL`, `AGRONOMO` ou `GESTOR_TECNOLOGIA`).
- **FR-002**: O sistema MUST garantir que o email seja único entre todas as contas; uma tentativa de cadastro com email já existente é recusada de forma explícita.
- **FR-003**: O sistema MUST exigir senha com no mínimo 8 caracteres e armazená-la sempre com hash, nunca em texto puro.
- **FR-004**: O sistema MUST permitir solicitar recuperação de senha por email, enviando um link de redefinição com token de expiração de 1 hora.
- **FR-005**: O sistema MUST rejeitar, com mensagem clara, um token de recuperação já utilizado ou expirado.
- **FR-006**: O sistema MUST responder de forma equivalente a uma solicitação de recuperação de senha para email existente ou inexistente, sem revelar qual é o caso.

### Key Entities

- **Conta de Usuário**: nome, email (único), senha (com hash) e papel associado.
- **Token de Recuperação de Senha**: credencial temporária de uso único, com expiração de 1 hora, associada a uma conta.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Uma pessoa nova completa o cadastro de conta em menos de 2 minutos.
- **SC-002**: 100% das tentativas de cadastro com email duplicado são recusadas antes de criar uma segunda conta.
- **SC-003**: 100% dos tokens de recuperação expirados ou já usados são rejeitados, sem exceção.

## Assumptions

- O envio de email (serviço de entrega) é uma dependência externa cuja escolha é um detalhe de implementação a definir no plano técnico, respeitando a restrição de custo zero sempre que possível.
- Esta feature não implementa o vínculo agrônomo↔propriedade (isso é escopo da feature de Perfis de Acesso e Permissões, 014).
