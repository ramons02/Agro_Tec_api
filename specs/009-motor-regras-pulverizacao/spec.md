# Feature Specification: Motor de Regras e Alerta de Janela Segura para Pulverização

**Feature Branch**: `009-motor-regras-pulverizacao`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "HU-09: o piloto de pulverização ou agrônomo recebe a classificação da janela de pulverização (Favorável, Bloqueado por Vento Forte, Bloqueado por Inversão Térmica), para que a aplicação de defensivos não sofra perda por deriva ou evaporação acelerada."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Classificar a janela de pulverização (Priority: P1)

Dadas as condições de vento atuais de uma estação, o sistema classifica se a pulverização está liberada ou bloqueada, e por qual motivo.

**Why this priority**: é a funcionalidade central do produto para o caso de uso de pulverização — sem ela, o usuário não tem nenhuma orientação de segurança operacional.

**Independent Test**: fornecer uma leitura de vento e rajada e confirmar que a classificação retornada corresponde exatamente à regra esperada para aquele valor.

**Acceptance Scenarios**:

1. **Given** velocidade do vento entre 3 km/h e 10 km/h e rajada até 15 km/h, **When** o sistema classifica a janela, **Then** o resultado é **Favorável**.
2. **Given** velocidade do vento acima de 10 km/h ou rajada acima de 15 km/h, **When** o sistema classifica a janela, **Then** o resultado é **Bloqueado por Vento Forte**.
3. **Given** velocidade do vento abaixo de 3 km/h, **When** o sistema classifica a janela, **Then** o resultado é **Bloqueado por Inversão Térmica**, independentemente de qualquer outra leitura.

---

### User Story 2 - Exibir alerta visual destacado (Priority: P2)

O resultado da classificação aparece em destaque visual, para leitura instantânea sem interpretação adicional.

**Why this priority**: a classificação (User Story 1) só gera valor prático se for percebida rapidamente pelo usuário no campo — complementa, mas depende dela.

**Independent Test**: com uma classificação já calculada, confirmar que ela é exibida como um indicador visual de alerta distinto por tipo de resultado.

**Acceptance Scenarios**:

1. **Given** uma classificação de janela de pulverização calculada, **When** ela é exibida na tela, **Then** aparece em um indicador visual de alerta que diferencia claramente Favorável de cada tipo de bloqueio.

---

### Edge Cases

- O que acontece exatamente nos limites das faixas (ex.: vento a exatamente 10 km/h ou 3 km/h)? Os limites são inclusivos na faixa Favorável (3 a 10 km/h inclusive); abaixo de 3 bloqueia por inversão térmica, acima de 10 bloqueia por vento forte.
- O que acontece se não houver leitura de vento disponível para a estação mais próxima? O sistema não deve apresentar uma classificação como se fosse válida — deve sinalizar ausência de dado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST classificar a janela de pulverização como **Favorável** quando a velocidade do vento estiver entre 3 km/h e 10 km/h (inclusive) e a rajada não ultrapassar 15 km/h.
- **FR-002**: O sistema MUST classificar a janela como **Bloqueado por Vento Forte** quando a velocidade do vento ultrapassar 10 km/h ou a rajada ultrapassar 15 km/h.
- **FR-003**: O sistema MUST classificar a janela como **Bloqueado por Inversão Térmica** quando a velocidade do vento for inferior a 3 km/h, independentemente de qualquer outra condição.
- **FR-004**: O sistema MUST exibir o resultado da classificação em um indicador visual de alerta, distinto para cada um dos três estados possíveis.
- **FR-005**: O sistema MUST basear a classificação na leitura de vento da estação meteorológica mais próxima do talhão em questão.

### Key Entities

- **Classificação de Janela de Pulverização**: um entre três estados mutuamente exclusivos (Favorável, Bloqueado por Vento Forte, Bloqueado por Inversão Térmica), derivado da leitura de vento/rajada mais recente.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% das classificações de janela de pulverização correspondem exatamente às faixas de vento/rajada definidas, verificável por teste automatizado com valores de fronteira.
- **SC-002**: Um usuário identifica se a pulverização está liberada ou bloqueada em menos de 3 segundos ao abrir a tela.

## Assumptions

- A regra de inversão térmica depende apenas da velocidade do vento (< 3 km/h), sem necessidade de leitura adicional de variação de temperatura — decisão já fechada em `requisitos/REQUISITOS.md` (RN003, que supera a antiga RN014).
- Esta feature consome a leitura de vento da estação mais próxima calculada pela feature 006; não recalcula proximidade.
