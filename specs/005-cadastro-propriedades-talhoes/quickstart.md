# Quickstart: Cadastro Territorial de Propriedades e Talhões

## Cenário 1 — Cadastro básico

Criar uma propriedade, depois um talhão com polígono válido vinculado a ela.

**Esperado**: ambos consultáveis via GET, talhão vinculado corretamente à propriedade.

## Cenário 2 — Sobreposição bloqueada

Criar um segundo talhão na mesma propriedade sobrepondo o primeiro em mais de 10m².

**Esperado**: HTTP 409 com `tipo: "SOBREPOSICAO"`.

## Cenário 3 — Fora do Pará com confirmação

Criar um talhão com centroide fora da bounding box do Pará, sem `confirmar_fora_do_para`, depois repetir com `confirmar_fora_do_para: true`.

**Esperado**: primeira tentativa retorna 422 pedindo confirmação; segunda tentativa é aceita.

## Cenário 4 — Exclusão em cascata

Excluir a propriedade criada no Cenário 1.

**Esperado**: o talhão vinculado também deixa de existir.

## Validação de sucesso

Feature validada quando os 4 cenários se comportam exatamente como descrito.
