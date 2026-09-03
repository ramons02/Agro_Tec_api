# Contract: POST /api/v1/auth/login

## Request

```json
{
  "email": "produtor@exemplo.com",
  "senha": "string (min 8 caracteres)"
}
```

## Response 200 (sucesso)

```json
{
  "status": "sucesso",
  "data_consulta_utc": "2026-09-03T12:00:00Z",
  "dados": {
    "token": "eyJhbGciOi...",
    "expira_em": "2026-09-04T12:00:00Z",
    "papel": "PRODUTOR_RURAL"
  }
}
```

## Response 401 (credenciais inválidas)

```json
{
  "status": "erro",
  "codigo": 401,
  "mensagem": "Usuário ou senha inválidos.",
  "detalhes": null
}
```

Nota: a mensagem de erro nunca distingue "usuário não existe" de "senha incorreta" (evita enumeração de contas).

## Contract: Dependência de Autenticação (aplicada a toda rota protegida)

**Header exigido**: `Authorization: Bearer <token>`

### Response 401 (token ausente, inválido ou expirado)

```json
{
  "status": "erro",
  "codigo": 401,
  "mensagem": "Token de autorização ausente ou expirado.",
  "detalhes": null
}
```
