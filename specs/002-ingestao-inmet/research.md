# Phase 0 Research: Ingestão Assíncrona de Dados das Estações do INMET

## Cadência de ingestão

- **Decision**: rodar o job de ingestão a cada 10 minutos por estação.
- **Rationale**: o INMET publica medições automáticas em intervalos horários/decendiais conforme estação; 10 minutos garante que uma medição nova nunca fique mais de 10 min sem ser capturada, com folga confortável para o limite de staleness de 30 min usado pela feature 008.
- **Alternatives considered**: ingestão sob demanda (só quando alguém consulta) — rejeitada porque geraria latência de até 3s (timeout) na primeira consulta de cada usuário; ingestão a cada 1 min — desnecessária dado que o INMET não publica dados novos nessa cadência.

## Timeout e fallback

- **Decision**: timeout de 3.0s por chamada HTTP ao INMET (via `httpx.Timeout`); no timeout ou erro HTTP, aciona a busca equivalente via Open-Meteo (feature 003) para a mesma coordenada da estação.
- **Rationale**: valor explícito no RN009/Constituição §III.
- **Alternatives considered**: retry com backoff antes do fallback — rejeitado para não estourar o orçamento de tempo de resposta (RNF002, 2s de orçamento total para o usuário final).

## Deduplicação

- **Decision**: chave única `(estacao_codigo, data_hora_utc)` na tabela `medicoes_clima`; inserção usa upsert (`ON CONFLICT DO NOTHING` ou `DO UPDATE` se o valor mudou).
- **Rationale**: evita duplicidade em reprocessamento (o mesmo instante pode ser buscado por dois ciclos do job em condições de borda).

## Agendador em segundo plano

- **Decision**: APScheduler embutido no processo da API (job store em memória), conforme já definido na Escopo Técnico (Camada "Agendador ETL").
- **Rationale**: evita a complexidade operacional de um worker Celery + Redis separado no MVP, mantendo custo de infraestrutura baixo; documentado como o caminho definido pela Constituição/Escopo.
- **Alternatives considered**: Celery + Redis — mantido como opção futura caso o volume de estações/frequência cresça a ponto de exigir um worker dedicado; não justificado ainda pelo Scale/Scope desta feature.

## Retenção e agregação (RNF014)

- **Decision**: um job diário separado compacta medições com mais de 12 meses em agregados diários (média/soma conforme a variável) e remove a granularidade horária original desse período.
- **Rationale**: requisito explícito já validado com o dono do produto.
