# Feature Specification: Cadastro Territorial de Propriedades e Talhões com Polígonos

**Feature Branch**: `005-cadastro-propriedades-talhoes`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "HU-05: um gestor agrícola cadastra propriedades rurais e delimita talhões informando ou desenhando polígonos espaciais, para que o sistema realize análises agroclimáticas personalizadas por área."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cadastrar propriedade e talhão com polígono (Priority: P1)

Um produtor cadastra uma propriedade e um ou mais talhões, cada um delimitado por um polígono geográfico.

**Why this priority**: é o dado fundacional de todo o produto — nenhuma análise climática, de solo ou de pulverização existe sem um talhão georreferenciado cadastrado.

**Independent Test**: cadastrar uma propriedade nova e um talhão com um polígono válido, e confirmar que ambos ficam disponíveis para consulta e vinculados entre si.

**Acceptance Scenarios**:

1. **Given** os dados de uma propriedade, **When** o usuário a cadastra, **Then** ela fica disponível para consulta e para receber talhões vinculados.
2. **Given** uma propriedade já cadastrada, **When** o usuário cadastra um talhão informando um polígono válido, **Then** o talhão fica vinculado a essa propriedade e disponível para consulta.
3. **Given** uma propriedade com talhões vinculados, **When** a propriedade é excluída, **Then** todos os seus talhões são excluídos junto (cascata).

---

### User Story 2 - Importar geometria de arquivo (Priority: P2)

Um usuário importa a geometria de um talhão a partir de um arquivo geográfico em vez de desenhar manualmente.

**Why this priority**: reduz o esforço de cadastro para quem já possui a geometria da propriedade em um sistema externo (ex.: agrimensura), mas o cadastro manual (User Story 1) já entrega valor sem essa importação.

**Independent Test**: importar um arquivo de geometria válido e confirmar que o polígono do talhão é preenchido corretamente a partir dele.

**Acceptance Scenarios**:

1. **Given** um arquivo de geometria válido (GeoJSON, KML ou Shapefile), **When** o usuário o importa no cadastro de talhão, **Then** o polígono do talhão é preenchido a partir do arquivo, sem necessidade de desenho manual.

---

### User Story 3 - Impedir sobreposição indevida (Priority: P2)

O sistema impede que dois talhões da mesma propriedade se sobreponham de forma inválida.

**Why this priority**: protege a integridade dos dados usados em todos os cálculos por área (ha), evitando dupla contagem de área e resultados incorretos de balanço hídrico/pulverização.

**Independent Test**: tentar cadastrar um talhão sobreposto a outro já existente na mesma propriedade e confirmar que o cadastro é bloqueado com mensagem explicativa.

**Acceptance Scenarios**:

1. **Given** um talhão já cadastrado em uma propriedade, **When** o usuário tenta cadastrar outro talhão com sobreposição significativa de área na mesma propriedade, **Then** o cadastro é bloqueado com mensagem explicando o motivo.
2. **Given** um talhão de uma propriedade, **When** ele se sobrepõe a um talhão de uma propriedade diferente, **Then** o cadastro é permitido, com um aviso (pode ser uma divisa em disputa, fora do escopo do sistema resolver).

---

### Edge Cases

- O que acontece quando o centroide de um talhão cai fora de uma área aproximada do estado do Pará? O cadastro é aceito mediante confirmação explícita do usuário, nunca bloqueado (pode ser dado de fronteira legítimo).
- O que acontece se o arquivo importado tiver mais de um polígono? Assume-se o primeiro polígono válido do arquivo como o talhão (ver Assumptions).
- O que acontece ao excluir um talhão isoladamente (sem excluir a propriedade)? Apenas o talhão é removido; a propriedade e os demais talhões permanecem intactos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST permitir criar, consultar, atualizar e excluir propriedades.
- **FR-002**: O sistema MUST permitir criar, consultar, atualizar e excluir talhões, cada um vinculado a exatamente uma propriedade.
- **FR-003**: O sistema MUST excluir automaticamente todos os talhões de uma propriedade quando essa propriedade for excluída.
- **FR-004**: O sistema MUST validar que a geometria de um talhão ou propriedade é um polígono geograficamente válido antes de persistir.
- **FR-005**: O sistema MUST permitir importar a geometria de um talhão a partir de um arquivo geográfico (GeoJSON, KML ou Shapefile).
- **FR-006**: O sistema MUST bloquear o cadastro de um talhão cuja geometria se sobreponha significativamente a outro talhão da mesma propriedade.
- **FR-007**: O sistema MUST permitir, com aviso (sem bloquear), a sobreposição de talhões pertencentes a propriedades diferentes.
- **FR-008**: O sistema MUST aceitar, mediante confirmação explícita do usuário, um talhão cujo centroide caia fora da área aproximada do estado do Pará.
- **FR-009**: O sistema MUST paginar listagens de propriedades e talhões acima de 50 itens, com 20 itens por página como padrão.

### Key Entities

- **Propriedade**: unidade rural com nome, proprietário e geometria própria; possui um ou mais talhões.
- **Talhão**: subdivisão de uma propriedade com geometria própria (polígono), pertencente a exatamente uma propriedade, com tipo de solo associado (ver feature de Solo).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um usuário consegue cadastrar uma propriedade e um talhão com geometria válida em menos de 3 minutos, sem apoio técnico.
- **SC-002**: 100% das tentativas de sobreposição de talhão dentro da mesma propriedade são bloqueadas antes da persistência.
- **SC-003**: Um arquivo de geometria válido é importado e reconhecido corretamente em pelo menos 95% das tentativas.

## Assumptions

- Quando um arquivo importado contém múltiplos polígonos (ex.: FeatureCollection), o sistema considera o primeiro polígono válido como o talhão — múltiplos talhões por arquivo não são criados automaticamente nesta feature.
- "Sobreposição significativa" segue o limiar já definido em `requisitos/REQUISITOS.md` (RN015: área de sobreposição maior que 10m²).
- Suporte a formatos de arquivo além de GeoJSON, KML e Shapefile está fora de escopo.
