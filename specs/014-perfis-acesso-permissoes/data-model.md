# Data Model: Perfis de Acesso e Permissões

## VinculoAgronomoPropriedade

| Campo | Tipo | Regras |
|---|---|---|
| `id` | UUID | chave primária |
| `agronomo_id` | UUID | FK → `Usuario` (papel deve ser `AGRONOMO`) |
| `propriedade_id` | UUID | FK → `Propriedade` |
| `estado` | enum | `CONVIDADO` \| `ACEITO` \| `REVOGADO` |
| `convidado_em` | timestamp | |
| `aceito_em` | timestamp, nullable | |

## Extensão de Propriedade (feature 005)

Já possui `proprietario_id` — usado diretamente pela regra de autorização de `PRODUTOR_RURAL`.
