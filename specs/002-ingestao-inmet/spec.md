# Feature Specification: Ingestão Assíncrona de Dados das Estações do INMET

**Feature Branch**: `002-ingestao-inmet`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "HU-02: o sistema consome assincronamente os dados da API pública do INMET para as estações meteorológicas automáticas do Pará, para que as medições de precipitação, temperatura, umidade, vento e rajadas fiquem armazenadas e disponíveis."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Captura periódica de medições das estações do Pará (Priority: P1)

O sistema busca continuamente as medições mais recentes de cada estação meteorológica automática do INMET no Pará e as mantém disponíveis para consulta.

**Why this priority**: toda a inteligência do produto (pulverização, plantio, mapa) depende de haver dados climáticos reais armazenados; sem isso não há nada para calcular.

**Independent Test**: disparar uma rodada de ingestão e verificar que medições novas de pelo menos uma estação ficam disponíveis para consulta, sem depender de nenhuma outra feature.

**Acceptance Scenarios**:

1. **Given** uma estação do INMET no Pará com medição nova disponível, **When** o sistema executa a ingestão, **Then** a medição (chuva, temperatura, umidade, vento, rajada) fica registrada e associada ao código da estação.
2. **Given** uma rodada de ingestão em andamento, **When** ela está buscando dados de uma estação, **Then** o restante do sistema continua respondendo normalmente a outras requisições (a busca não bloqueia a aplicação).

---

### User Story 2 - Continuidade quando o INMET falha (Priority: P2)

Quando a fonte primária (INMET) não responde a tempo, o sistema busca o dado equivalente em uma fonte alternativa, para que a ausência de dado nunca pare o produto.

**Why this priority**: o INMET é um serviço público sem SLA garantido; sem um plano de contingência, uma instabilidade pontual do INMET tiraria o sistema inteiro do ar para efeitos práticos.

**Independent Test**: simular indisponibilidade/timeout do INMET e confirmar que uma medição equivalente é obtida pela fonte alternativa dentro do tempo esperado.

**Acceptance Scenarios**:

1. **Given** a fonte primária não responde em até 3 segundos, **When** o sistema tenta obter a medição, **Then** ele busca o dado equivalente na fonte alternativa (previsão/observação do Open-Meteo) automaticamente.

---

### Edge Cases

- O que acontece se uma estação do INMET for desativada ou remanejada? A ingestão deve continuar para as demais estações sem falhar por completo.
- O que acontece se o mesmo instante de medição for recebido duas vezes (reprocessamento)? A medição não deve ser duplicada no armazenamento.
- Como o sistema trata medições com mais de 12 meses? São compactadas em agregados diários — granularidade horária original não é mantida além desse período (RNF014).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST buscar periodicamente as medições das estações automáticas do INMET localizadas no estado do Pará, sem bloquear outras operações do sistema durante a busca.
- **FR-002**: O sistema MUST registrar, para cada medição capturada, ao menos precipitação, temperatura, umidade relativa, velocidade do vento e rajada, associadas ao código da estação e ao instante da medição.
- **FR-003**: O sistema MUST acionar automaticamente uma fonte alternativa de dados climáticos quando a fonte primária não responder em até 3 segundos.
- **FR-004**: O sistema MUST operar sem custo de bilhetagem para qualquer integração usada na ingestão.
- **FR-005**: O sistema MUST reter medições com granularidade horária por até 12 meses, compactando em agregados diários após esse período.
- **FR-006**: O sistema MUST evitar o registro duplicado da mesma medição (mesma estação, mesmo instante).

### Key Entities

- **Estação Meteorológica**: ponto físico do INMET no Pará, identificado por um código único, com nome de município associado.
- **Medição de Clima**: registro de precipitação, temperatura, umidade, vento e rajada, associado a uma estação e a um instante específico; carrega a indicação de qual fonte originou o dado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Medições de todas as estações ativas do INMET no Pará ficam disponíveis para consulta em até 1 hora após serem publicadas pela fonte.
- **SC-002**: Quando a fonte primária falha, uma medição equivalente continua disponível em até 3 segundos adicionais de espera, sem intervenção manual.
- **SC-003**: Zero custo de bilhetagem gerado pela ingestão de dados, verificável em qualquer período de operação.

## Assumptions

- A lista de estações do INMET no Pará é conhecida e relativamente estável (adição/remoção de estação é um evento raro, não um fluxo de usuário desta feature).
- "Assincronamente" refere-se ao comportamento não-bloqueante do sistema, não a uma frequência específica de coleta — a cadência exata da ingestão periódica é um detalhe de implementação a ser definido no plano técnico.
- A fonte alternativa mencionada na User Story 2 é a mesma integração especificada na feature de Previsão Climática (Open-Meteo).
