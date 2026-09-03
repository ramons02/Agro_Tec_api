# Feature Specification: Identificação Espacial da Estação INMET Mais Próxima

**Feature Branch**: `006-estacao-mais-proxima`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "HU-06: o sistema identifica instantaneamente a estação do INMET mais próxima de um talhão, para que as condições meteorológicas usadas nas decisões representem com precisão o microclima local."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Vincular talhão à estação mais próxima (Priority: P1)

Dado um talhão cadastrado, o sistema identifica qual estação meteorológica do INMET está fisicamente mais próxima dele.

**Why this priority**: toda decisão de pulverização e monitoramento em tempo real (HU-08, HU-09) depende de saber qual estação representa o microclima daquele talhão específico.

**Independent Test**: consultar a estação mais próxima de um talhão com coordenada conhecida e confirmar que o resultado é a estação fisicamente mais próxima entre as cadastradas.

**Acceptance Scenarios**:

1. **Given** um talhão com geometria cadastrada, **When** o sistema busca a estação mais próxima, **Then** retorna o código da estação, o nome do município e a distância em quilômetros até o talhão.
2. **Given** duas estações a distâncias diferentes de um talhão, **When** a busca é executada, **Then** a estação retornada é sempre a de menor distância.

---

### Edge Cases

- O que acontece se não houver nenhuma estação do INMET num raio razoável do talhão (área muito remota)? O sistema deve retornar a estação mais próxima disponível de qualquer forma, com a distância informada, para que o usuário avalie a representatividade.
- O que acontece se duas estações estiverem exatamente à mesma distância? Qualquer uma das duas pode ser retornada de forma consistente entre chamadas (empate não gera erro).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST identificar, para um talhão dado, a estação meteorológica fisicamente mais próxima entre as estações cadastradas.
- **FR-002**: O sistema MUST retornar o código da estação, o nome do município e a distância em quilômetros até o talhão consultado.
- **FR-003**: O sistema MUST responder a essa consulta em menos de 100 milissegundos.

### Key Entities

- **Estação Meteorológica**: ponto geográfico do INMET com código único e nome de município (definida na feature de Ingestão INMET).
- **Talhão**: área geográfica cujo centroide é usado como referência de distância (definido na feature de Cadastro Territorial).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A busca da estação mais próxima responde em menos de 100 milissegundos em 99% das consultas.
- **SC-002**: 100% das respostas retornam a estação de menor distância real entre as cadastradas, verificável por auditoria geométrica.

## Assumptions

- A referência de distância do talhão é o seu centroide geométrico, não um ponto arbitrário da sua borda.
- Esta feature depende de talhões (Cadastro Territorial) e estações (Ingestão INMET) já existirem; não cadastra nenhuma das duas.
