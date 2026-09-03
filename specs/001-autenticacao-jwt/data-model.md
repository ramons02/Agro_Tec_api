# Data Model: Autenticação e Segurança de Usuário via Token JWT

## Usuario

Representa uma conta autenticável no sistema. O cadastro (criação) é escopo da feature 013; esta feature apenas consome a entidade para validar login.

| Campo | Tipo | Regras |
|---|---|---|
| `id` | UUID | chave primária |
| `email` | string | único, usado como identificador de login |
| `senha_hash` | string | nunca exposto em nenhuma resposta da API |
| `papel` | enum | `PRODUTOR_RURAL` \| `AGRONOMO` \| `GESTOR_TECNOLOGIA` (RD009) — usado por outras features (014) para autorização |
| `criado_em` | timestamp | auditoria |

## Token de Autenticação (não persistido)

Não é uma entidade de banco — é um artefato stateless (JWT) computado a partir do `Usuario`.

| Claim | Descrição |
|---|---|
| `sub` | id do usuário |
| `papel` | papel do usuário (evita consulta ao banco a cada requisição autenticada) |
| `exp` | expiração, 24h após emissão (RNF009) |

## Relacionamentos

- `Usuario` é referenciado por `Propriedade` (dono), por `Vínculo Agrônomo-Propriedade` (feature 014) e por qualquer registro de auditoria futuro — mas esta feature não modela essas relações, apenas a entidade base.
