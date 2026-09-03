# Feature Specification: Autenticação e Segurança de Usuário via Token JWT

**Feature Branch**: `001-autenticacao-jwt`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "HU-01: produtor rural, agrônomo ou gestor de tecnologia realiza login seguro na plataforma com credenciais válidas e recebe um token JWT, para que todas as requisições ao backend sejam autenticadas e autorizadas, protegendo os dados da propriedade."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Login com credenciais válidas (Priority: P1)

Um usuário já cadastrado (produtor rural, agrônomo ou gestor de tecnologia) informa usuário e senha e recebe acesso autenticado à plataforma.

**Why this priority**: sem autenticação, nenhuma outra funcionalidade do sistema pode ser exposta com segurança — é o pré-requisito de todas as demais features.

**Independent Test**: enviar credenciais válidas e confirmar que a resposta contém um token de sessão válido, testável isoladamente sem depender de nenhuma outra feature.

**Acceptance Scenarios**:

1. **Given** um usuário cadastrado com credenciais corretas, **When** ele envia usuário e senha, **Then** o sistema retorna um token de autenticação válido por 24 horas.
2. **Given** um usuário cadastrado, **When** ele envia uma senha incorreta, **Then** o sistema rejeita o login sem informar se o erro foi no usuário ou na senha.

---

### User Story 2 - Bloqueio de acesso não autenticado (Priority: P1)

Qualquer requisição a uma área protegida do sistema sem um token válido é recusada.

**Why this priority**: é a garantia central de proteção dos dados da propriedade — sem ela, a autenticação da User Story 1 não tem efeito prático.

**Independent Test**: chamar uma rota protegida sem token, com token expirado e com token malformado, e confirmar que todas são recusadas.

**Acceptance Scenarios**:

1. **Given** uma rota protegida, **When** a requisição não inclui token de autenticação, **Then** o sistema recusa o acesso com um erro de não autorizado e mensagem legível.
2. **Given** uma rota protegida, **When** a requisição inclui um token expirado ou inválido, **Then** o sistema recusa o acesso da mesma forma que a ausência de token.

---

### Edge Cases

- O que acontece quando o token expira no meio de uma sessão ativa do usuário? O sistema deve recusar a próxima requisição e o cliente deve redirecionar para o login.
- Como o sistema trata tentativas repetidas de login com senha errada? Fora de escopo desta feature (ver Assumptions) — nenhum bloqueio por tentativas é exigido aqui.
- O que acontece se as chaves de assinatura do token não estiverem configuradas no ambiente? O sistema deve falhar de forma explícita na inicialização, nunca operar sem segredo configurado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST disponibilizar um mecanismo de login que valide usuário e senha.
- **FR-002**: O sistema MUST emitir um token de autenticação com validade de 24 horas quando as credenciais forem válidas.
- **FR-003**: O sistema MUST exigir um token de autenticação válido em toda rota que exponha dados de propriedades, talhões ou medições.
- **FR-004**: O sistema MUST recusar com um erro de não autorizado (e mensagem legível, sem detalhes sensíveis) qualquer requisição com token ausente, expirado ou inválido.
- **FR-005**: O sistema MUST carregar chaves de assinatura e demais segredos de autenticação exclusivamente de configuração de ambiente, nunca de valores fixos no código-fonte ou no controle de versão.

### Key Entities

- **Usuário**: pessoa autenticável no sistema (produtor rural, agrônomo ou gestor de tecnologia — o papel em si é escopo da feature de Perfis de Acesso); possui credenciais de login.
- **Token de Autenticação**: credencial temporária emitida no login, com tempo de expiração, usada para autorizar requisições subsequentes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um usuário com credenciais válidas completa o login e recebe acesso autenticado em menos de 2 segundos.
- **SC-002**: 100% das requisições a rotas protegidas sem token válido são recusadas, sem exceção.
- **SC-003**: Nenhum segredo de autenticação aparece em texto plano no repositório de código-fonte, verificável por auditoria.

## Assumptions

- O cadastro de conta (criação de usuário) é tratado pela feature de Cadastro de Conta e Recuperação de Senha (HU-13) — esta feature assume que usuários já existem.
- Bloqueio por tentativas repetidas de login (rate limiting/lockout) não é requisito desta feature na V1.
- A validade de 24 horas para o token é a definida na Constituição do Projeto/Convenção Técnica e não é configurável por usuário.
