# Quickstart: Recomendação Acionável de "Próximo Passo"

## Cenário 1 — Prioridade Alta

Status de plantio Vermelho + pulverização Favorável → prioridade `ALTA` (RN011, independe da pulverização).

## Cenário 2 — Prioridade Média por bloqueio de pulverização

Status de plantio Verde + pulverização Bloqueada → prioridade `MEDIA` (RN012).

## Cenário 3 — Tendência em status Amarelo

Status Amarelo com umidade subindo 2 p.p. em 3 dias → texto indica melhora. Com queda de 2 p.p. → texto indica piora. Com variação de 0,5 p.p. → texto indica estabilidade.

## Validação de sucesso

Feature validada quando os 3 cenários retornam a prioridade e o texto esperados, e todo resultado inclui o aviso fixo de sugestão automática.
