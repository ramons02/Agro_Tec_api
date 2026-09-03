# Data Model: Cadastro de Conta e Recuperação de Senha

## Extensão de Usuario (feature 001 já define a entidade base)

Sem novos campos além dos já definidos (`nome` precisa ser adicionado se não existia): `nome`, `email` (único), `senha_hash`, `papel`.

## TokenRecuperacaoSenha

| Campo | Tipo | Regras |
|---|---|---|
| `id` | UUID | chave primária |
| `usuario_id` | UUID | FK → `Usuario` |
| `token` | string | único, opaco |
| `expira_em` | timestamp | criado_em + 1 hora |
| `usado_em` | timestamp, nullable | preenchido no momento do uso; um token com `usado_em` não nulo é sempre rejeitado |
