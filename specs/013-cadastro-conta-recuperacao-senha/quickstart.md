# Quickstart: Cadastro de Conta e Recuperação de Senha

## Cenário 1 — Cadastro e login em seguida

Registrar uma conta nova e usá-la imediatamente no endpoint de login (feature 001).

**Esperado**: login bem-sucedido com as credenciais recém-criadas.

## Cenário 2 — Email duplicado

Registrar duas vezes com o mesmo email.

**Esperado**: segunda tentativa retorna HTTP 409.

## Cenário 3 — Recuperação e redefinição

Solicitar recuperação, capturar o token gerado (em ambiente de teste, sem envio real de email), redefinir a senha, e confirmar login com a nova senha.

**Esperado**: fluxo completo funciona; usar o mesmo token uma segunda vez retorna HTTP 400.

## Cenário 4 — Não vazamento de existência de email

Solicitar recuperação para um email cadastrado e para um não cadastrado.

**Esperado**: resposta idêntica em ambos os casos.

## Validação de sucesso

Feature validada quando os 4 cenários se comportam exatamente como descrito.
