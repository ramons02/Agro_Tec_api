# Contract: Cadastro de Conta e Recuperação de Senha

## POST /api/v1/auth/registro

**Request**: `{"nome": "...", "email": "...", "senha": "min 8 chars", "papel": "PRODUTOR_RURAL"}`

**Response 201**: usuário criado (sem `senha_hash` no payload de resposta).

**Response 409**: `{"status": "erro", "codigo": 409, "mensagem": "Email já cadastrado.", "detalhes": null}`

## POST /api/v1/auth/recuperar-senha

**Request**: `{"email": "..."}`

**Response 200 (sempre, exista ou não o email)**:
```json
{"status": "sucesso", "data_consulta_utc": "...", "dados": {"mensagem": "Se o email existir, um link de redefinição foi enviado."}}
```

## POST /api/v1/auth/redefinir-senha

**Request**: `{"token": "...", "nova_senha": "min 8 chars"}`

**Response 200**: senha redefinida.

**Response 400 (token expirado ou já usado)**:
```json
{"status": "erro", "codigo": 400, "mensagem": "Link de redefinição inválido ou expirado. Solicite um novo.", "detalhes": null}
```
