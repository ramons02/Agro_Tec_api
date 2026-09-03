# Contract: Vínculo Agrônomo-Propriedade e Autorização

## POST /api/v1/propriedades/{id}/vinculos

**Request**: `{"agronomo_email": "..."}` — apenas o dono da propriedade ou `GESTOR_TECNOLOGIA` pode convidar.

**Response 201**: vínculo criado com `estado: "CONVIDADO"`.

## POST /api/v1/vinculos/{id}/aceitar

Executado pelo próprio agrônomo convidado.

**Response 200**: vínculo passa a `estado: "ACEITO"`.

## Exemplo de bloqueio de escrita (qualquer endpoint de propriedades/talhões)

**Response 403**:
```json
{"status": "erro", "codigo": 403, "mensagem": "Você não tem permissão para editar esta propriedade.", "detalhes": {"papel": "AGRONOMO"}}
```
