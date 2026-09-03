# Quickstart: Perfis de Acesso e Permissões

## Cenário 1 — Produtor não edita propriedade de outro

Com dois produtores e suas propriedades, produtor A tenta editar propriedade de B.

**Esperado**: HTTP 403.

## Cenário 2 — Agrônomo sem vínculo não vê a propriedade

Consultar propriedades como agrônomo sem nenhum vínculo aceito.

**Esperado**: lista vazia, nunca as propriedades de terceiros.

## Cenário 3 — Convite e aceite

Convidar um agrônomo para uma propriedade; antes do aceite, ele não tem acesso; depois do aceite, tem acesso de leitura.

**Esperado**: acesso muda exatamente no momento do aceite, nunca antes.

## Cenário 4 — Gestor de tecnologia tem acesso total

Com um usuário `GESTOR_TECNOLOGIA`, acessar e editar uma propriedade de qualquer produtor.

**Esperado**: acesso de leitura e escrita concedido sem restrição.

## Validação de sucesso

Feature validada quando os 4 cenários se comportam exatamente como descrito.
