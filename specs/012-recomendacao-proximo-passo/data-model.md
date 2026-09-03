# Data Model: Recomendação Acionável de "Próximo Passo"

Nenhuma entidade persistida. Objeto de resposta (não persistido):

## Recomendacao

| Campo | Tipo |
|---|---|
| `texto` | string |
| `prioridade` | enum: `ALTA` \| `MEDIA` \| `BAIXA` |
| `aviso` | string (fixo: "Sugestão gerada automaticamente — não substitui avaliação agronômica profissional") |

Entradas consumidas (já modeladas em outras features): `status_plantio` (011), `classificacao_pulverizacao` (009), série de `armazenamento_mm` dos últimos 3 dias (010).
