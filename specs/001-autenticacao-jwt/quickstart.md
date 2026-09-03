# Quickstart: Autenticação e Segurança de Usuário via Token JWT

## Pré-requisitos

- Backend rodando localmente (`uvicorn app.main:app --reload`)
- Um usuário já cadastrado no banco (via feature 013, ou inserido manualmente para teste)
- Variável de ambiente `JWT_SECRET` configurada em `.env`

## Cenário 1 — Login com sucesso

```bash
curl -X POST http://localhost:8000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "produtor@exemplo.com", "senha": "senha-valida-123"}'
```

**Esperado**: HTTP 200 com `dados.token` presente e `dados.expira_em` 24h à frente.

## Cenário 2 — Login com senha errada

Mesmo comando com senha incorreta.

**Esperado**: HTTP 401, mensagem genérica ("Usuário ou senha inválidos"), sem indicar se o email existe.

## Cenário 3 — Acesso a rota protegida sem token

```bash
curl http://localhost:8000/api/v1/propriedades
```

**Esperado**: HTTP 401, mensagem "Token de autorização ausente ou expirado."

## Cenário 4 — Acesso a rota protegida com token válido

```bash
TOKEN="<token obtido no Cenário 1>"
curl http://localhost:8000/api/v1/propriedades -H "Authorization: Bearer $TOKEN"
```

**Esperado**: HTTP 200 (ou lista vazia se não houver propriedades) — a rota não retorna 401.

## Validação de sucesso

Feature validada quando os 4 cenários acima se comportam exatamente como descrito, cobrindo as duas User Stories da spec (login válido e bloqueio de acesso não autenticado).
