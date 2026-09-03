# Phase 0 Research: Autenticação e Segurança de Usuário via Token JWT

Nenhum `[NEEDS CLARIFICATION]` restou na spec ou no Technical Context — a stack e as regras de segurança já estão fixadas na Constituição do repositório e na Convenção de Desenvolvimento. Este documento registra as decisões técnicas de suporte necessárias para a implementação.

## Biblioteca de JWT

- **Decision**: `python-jose[cryptography]` para emissão e validação do token, algoritmo HS256.
- **Rationale**: biblioteca madura no ecossistema FastAPI, com suporte nativo a expiração (`exp`) e assinatura simétrica — suficiente para um único backend emissor/validador (não há necessidade de chaves assimétricas com múltiplos emissores no MVP).
- **Alternatives considered**: `PyJWT` (equivalente em funcionalidade; `python-jose` escolhido por já ser o mais referenciado nos exemplos oficiais do FastAPI para este caso de uso).

## Hash de senha

- **Decision**: `passlib` com esquema `bcrypt`.
- **Rationale**: padrão de mercado para hash de senha com custo computacional ajustável, evita implementação própria de hashing.
- **Alternatives considered**: `argon2` (mais moderno, mas exige dependência nativa adicional sem ganho relevante para o volume de usuários do MVP).

## Estratégia de expiração e "logout"

- **Decision**: token stateless com expiração de 24h (RNF009); sem lista de revogação (blacklist) no MVP.
- **Rationale**: simplicidade — a Constituição não exige logout imediato server-side; expirar em 24h já limita a janela de exposição de um token vazado.
- **Alternatives considered**: blacklist de tokens revogados em Redis — descartado por adicionar uma dependência de infraestrutura sem requisito explícito que a justifique (RNF002 não exige logout imediato).

## Formato de erro 401

- **Decision**: seguir o envelope de erro padronizado da Convenção Técnica §5.2 (`{"status": "erro", "codigo": 401, "mensagem": ..., "detalhes": null}`).
- **Rationale**: consistência de contrato entre todas as rotas da API, conforme já definido na Convenção de Desenvolvimento.
