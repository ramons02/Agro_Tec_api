<!--
Sync Impact Report
Version change: 1.0.0 → 1.0.1
Modified principles: n/a
Added sections: n/a — added one clarifying sentence to "Fluxo de Desenvolvimento e Documentação" about the multi-repo commit boundary (already governed by Project Charter §8)
Removed sections: none
Templates requiring updates:
  ✅ .specify/templates/plan-template.md (no changes needed)
  ✅ .specify/templates/spec-template.md (no changes needed)
  ✅ .specify/templates/tasks-template.md (no changes needed)
  ✅ .specify/templates/commands/*.md (no changes needed)
Follow-up TODOs: none

---
Sync Impact Report
Version change: (none) → 1.0.0
Modified principles: n/a (initial ratification)
Added sections: Core Principles (I-V), Restrições Tecnológicas e de Custo, Fluxo de Desenvolvimento e Documentação, Governance
Removed sections: none
Templates requiring updates:
  ✅ .specify/templates/plan-template.md (Constitution Check section is generic, no changes needed)
  ✅ .specify/templates/spec-template.md (no agent-specific references found)
  ✅ .specify/templates/tasks-template.md (task categories remain compatible)
  ✅ .specify/templates/commands/*.md (no outdated agent-specific references found)
Follow-up TODOs: none
-->

# AgroClima Pará API Constitution

## Core Principles

### I. Assíncrono e Tipado (NON-NEGOTIABLE)
Toda função que realiza I/O externo (chamadas às APIs INMET, Open-Meteo, SoilGrids,
ou ao banco de dados) DEVE ser declarada com `async def`, usando bibliotecas
assíncronas (`httpx` para HTTP, `asyncpg`/SQLAlchemy Async para banco). Código
Python DEVE aderir à PEP 8 e usar Type Hints obrigatórios em toda assinatura de
função. Validação de entrada e saída da API é feita exclusivamente via Pydantic
Schemas v2 — nunca validação manual ad-hoc.
Rationale: o produto promete resposta em tempo real (RNF002: máximo 2s por
consulta) orquestrando três APIs externas em paralelo; bloqueio síncrono
inviabiliza essa promessa, e tipagem forte previne os erros de integração mais
comuns entre serviços externos heterogêneos.

### II. Custo Zero em Integrações Externas
Nenhuma integração externa (INMET, Open-Meteo, SoilGrids ou qualquer substituta)
pode exigir chave paga ou gerar custo de bilhetagem, tanto no MVP quanto em
produção. Limites gratuitos de uso (ex.: 10.000 requisições/dia no Open-Meteo)
DEVEM ser respeitados com cache e agendamento — nunca contornados com upgrade
pago sem nova decisão de produto.
Rationale: o orçamento do projeto para consumo de dados externos é de $0,00
(Constituição do Projeto, Restrição 5.2); é a premissa central do modelo de
negócio, não um detalhe de implementation.

### III. Tempo Real sem Cache Obsoleto
Toda rota de consulta climática atual (`/clima/atual` e equivalentes) DEVE
ignorar dados locais com mais de 30 minutos e disparar busca assíncrona
imediata nas fontes primárias. Falha ou timeout (>3s) do INMET aciona
obrigatoriamente o fallback para o Open-Meteo. Se INMET, Open-Meteo e
SoilGrids falharem simultaneamente, a API retorna a última medição válida em
cache com `fonte_dados: "CACHE_EXPIRADO"` — nunca um erro bloqueante. Toda
resposta de dados climáticos inclui o campo `fonte_dados: "AO_VIVO" |
"CACHE_EXPIRADO"`.
Rationale: decisões de pulverização e plantio dependem de dados do exato
momento; um dado velho apresentado como atual pode levar a perda de insumo ou
de safra. Ao mesmo tempo, o sistema nunca pode travar o usuário por
indisponibilidade de terceiros fora do controle do projeto.

### IV. Geoprocessamento Correto e Verificável
Toda geometria (propriedades, talhões, estações) é armazenada em PostGIS como
`GEOMETRY(Polygon|Point, 4326)` (SRID 4326/WGS 84). Consultas espaciais usam
operadores nativos do PostGIS (`<->` para distância, `ST_Overlaps`/`ST_Contains`
para interseção) — nunca cálculo de distância aproximado em código de
aplicação. A busca da estação INMET mais próxima de um talhão DEVE responder
em menos de 100ms (RNF003). Tabelas e colunas seguem `snake_case`, tabelas no
plural.
Rationale: geoprocessamento impreciso (ex.: haversine manual em vez de
PostGIS) diverge silenciosamente da spec matemática oficial
(`calculos-geo-metero.md`) e não escala; a convenção de nomenclatura evita
ambiguidade em um schema com múltiplas tabelas geoespaciais relacionadas.

### V. Segurança JWT e Segredos Fora do Git
Todas as rotas de consulta e cadastro exigem `Authorization: Bearer
<token_jwt>`; requisição com token ausente ou inválido é rejeitada com HTTP
401. Tokens JWT emitidos têm validade de 24 horas. Chaves de assinatura e
qualquer segredo NUNCA são commitados no repositório — são carregados
exclusivamente via `.env` (fora do controle de versão) e `core/config.py`
(`BaseSettings`). Apenas o dono de uma propriedade ou um usuário
`GESTOR_TECNOLOGIA` pode criar, editar ou excluir talhões; um `AGRONOMO`
tem acesso somente leitura.
Rationale: dados de propriedade rural e credenciais são sensíveis; vazamento
de segredo em Git é irreversível (histórico), e RBAC mal aplicado exporia
dados de um produtor a outro.

## Restrições Tecnológicas e de Custo

Stack aprovada e não substituível sem amendment desta constituição: Python
(FastAPI) no backend; PostgreSQL + PostGIS (SRID 4326) como banco; Vue.js 3 /
React + TypeScript no frontend; Leaflet.js para mapas; Axios + TanStack Query
para requisições sem cache; APScheduler/Celery + Redis para ETL em segundo
plano. Estrutura de pastas do backend segue o padrão definido na Convenção de
Desenvolvimento (`app/api/v1/endpoints`, `app/core`, `app/services`,
`app/db/models`, `app/main.py`). Respostas da API REST seguem sempre o
envelope padronizado `{"status": "sucesso"|"erro", ...}` documentado na
Convenção de Desenvolvimento §5.

## Fluxo de Desenvolvimento e Documentação

O `requisitos/REQUISITOS.md` e as HUs em `Agro_Tec_documentacao/HUs/` são a
fonte de verdade para requisitos funcionais, não funcionais e regras de
negócio — toda feature especificada via `/speckit-specify` DEVE referenciar o
RF/RN/HU correspondente quando existir. Branch principal é `main`; branches
de funcionalidade seguem `feature/nome-da-funcionalidade`. Commits seguem
Conventional Commits. Nenhum documento do projeto (README, specs, planos,
tasks) ou mensagem de commit pode conter emojis. Mensagens de commit nunca
citam Claude, Anthropic ou qualquer ferramenta de IA como autor ou coautor
(sem rodapé `Co-Authored-By: Claude` ou similar).
Rationale: manter uma única fonte de verdade evita divergência entre o que foi
prometido ao dono do produto e o que é implementado; a regra de commits/
documentação é uma decisão explícita do dono do produto, registrada também na
Constituição do Projeto (Project Charter) §8. O projeto é dividido em 4
repositórios (`Agro_Tec_documentacao`, `Agro_Tec_api`, `Agro_Tec_app`,
`Agro_Tec_infra`), cada um com responsabilidade única — todo commit feito
neste repositório deve conter apenas mudanças de backend; mudanças de
frontend, infra/`.env` ou documentação vão para o repositório correspondente,
nunca commitadas aqui (Project Charter §8).

## Governance

Esta constituição prevalece sobre qualquer prática de código, template ou
preferência individual em conflito. Emendas exigem: (1) proposta explícita do
motivo da mudança, (2) atualização deste arquivo com o Sync Impact Report no
topo, (3) verificação de que `plan-template.md`, `spec-template.md` e
`tasks-template.md` continuam consistentes com os princípios revisados, (4)
incremento de versão semântico — MAJOR para remoção/redefinição incompatível
de princípio, MINOR para princípio novo ou expansão material, PATCH para
clarificação de redação. Todo `/speckit-plan` DEVE incluir uma seção
"Constitution Check" verificando aderência aos cinco princípios centrais
antes de prosseguir para tasks; complexidade que viole um princípio precisa de
justificativa explícita registrada no plano.

**Version**: 1.0.1 | **Ratified**: 2026-09-03 | **Last Amended**: 2026-09-03
