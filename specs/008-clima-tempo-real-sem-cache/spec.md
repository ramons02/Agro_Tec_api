# Feature Specification: Consulta de Dados Climáticos em Tempo Real sem Cache Expirado

**Feature Branch**: `008-clima-tempo-real-sem-cache`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "HU-08: as consultas no dashboard trazem sempre as informações meteorológicas do exato momento sem retenção em cache antigo, para que decisões críticas de pulverização não sejam tomadas com base em dados ultrapassados."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consulta sempre atualizada (Priority: P1)

Um operador no campo consulta as condições climáticas atuais de um talhão e recebe sempre o dado mais recente disponível, nunca uma versão desatualizada guardada em cache do navegador.

**Why this priority**: é a garantia central de confiabilidade do produto — uma decisão de pulverização baseada em dado velho pode causar perda de insumo ou dano ambiental.

**Independent Test**: consultar o clima atual de um talhão duas vezes em sequência rápida e confirmar que a segunda consulta reflete qualquer mudança ocorrida entre as duas, sem retornar uma resposta em cache do navegador.

**Acceptance Scenarios**:

1. **Given** um talhão com medição local recente (menos de 30 minutos), **When** o usuário consulta o clima atual, **Then** o sistema retorna a medição já disponível sem nova busca externa.
2. **Given** um talhão cuja última medição local tem mais de 30 minutos, **When** o usuário consulta o clima atual, **Then** o sistema dispara imediatamente uma nova busca nas fontes primárias antes de responder.

---

### Edge Cases

- O que acontece se a busca imediata nas fontes primárias falhar no momento da consulta? O sistema retorna a última medição válida disponível, sinalizando explicitamente que o dado pode estar desatualizado (ver RN017 / feature de Ingestão INMET).
- O que acontece se dois usuários consultarem o mesmo talhão ao mesmo tempo, ambos gatilhando a busca por medição expirada? Apenas uma busca deve ser efetivamente realizada; ambos recebem o resultado atualizado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST identificar, a cada consulta de clima atual, se a última medição local do talhão tem mais de 30 minutos.
- **FR-002**: O sistema MUST disparar uma busca imediata nas fontes climáticas primárias quando a medição local estiver expirada (mais de 30 minutos), antes de responder à consulta.
- **FR-003**: O sistema MUST garantir que respostas de clima atual nunca sejam servidas a partir de cache de navegação desatualizado.
- **FR-004**: O sistema MUST identificar em cada resposta se o dado retornado é ao vivo ou uma última medição válida em cache expirado.

### Key Entities

- **Consulta de Clima Atual**: solicitação pontual do estado climático mais recente de um talhão, com indicação de quão atual é o dado retornado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Nenhuma consulta de clima atual retorna um dado com mais de 30 minutos sem sinalizar explicitamente que está desatualizado.
- **SC-002**: Uma consulta com medição expirada retorna o dado atualizado (ou o aviso de indisponibilidade) em até 5 segundos.
- **SC-003**: 100% das consultas de clima atual são verificáveis como não servidas por cache de navegador (via inspeção de cabeçalhos de resposta).

## Assumptions

- Esta feature descreve o comportamento observável pelo usuário; os detalhes de cabeçalho HTTP e parâmetros de invalidação de cache são decisões de implementação a documentar no plano técnico.
- Depende das features de Ingestão INMET (002) e Previsão Open-Meteo (003) como fontes primárias de busca imediata.
