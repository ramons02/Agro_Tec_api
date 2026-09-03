# Feature Specification: Exportação de Relatório de Talhões

**Feature Branch**: `015-exportacao-relatorio-talhoes`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "HU-15 (validada com o dono do produto em 2026-09-03, prioridade Baixa): o produtor rural ou gestor exporta a lista de talhões filtrada no Dashboard de Plantio em CSV, para levar os dados a uma planilha, relatório ou reunião."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Exportar talhões filtrados em CSV (Priority: P1)

O usuário exporta em CSV exatamente os talhões que estão visíveis na tela após aplicar filtros de propriedade e status.

**Why this priority**: é a única funcionalidade desta feature — sem ela, não há exportação alguma.

**Independent Test**: aplicar um filtro no Dashboard de Plantio, exportar e confirmar que o CSV gerado contém exatamente os talhões filtrados, não a lista completa.

**Acceptance Scenarios**:

1. **Given** uma lista de talhões filtrada por propriedade e/ou status, **When** o usuário exporta, **Then** o arquivo gerado contém apenas os talhões atualmente filtrados.
2. **Given** o arquivo exportado, **When** ele é aberto em uma planilha, **Then** contém ao menos propriedade, nome do talhão, área (ha), tipo de solo, status de plantio e umidade da camada 0-7cm.
3. **Given** o arquivo exportado, **When** aberto no Excel em português, **Then** os caracteres acentuados aparecem corretos (sem corrupção de encoding).

---

### Edge Cases

- O que acontece quando não há nenhum talhão para exportar (filtro sem resultados)? A ação de exportar deve estar desabilitada, evitando gerar um arquivo vazio.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST permitir exportar em CSV a lista de talhões atualmente filtrada no Dashboard de Plantio (respeitando os filtros de propriedade e status já aplicados).
- **FR-002**: O arquivo exportado MUST incluir, no mínimo, propriedade, nome do talhão, área (ha), tipo de solo, status de plantio e umidade da camada 0-7cm.
- **FR-003**: O arquivo exportado MUST abrir corretamente com acentuação em português quando aberto em planilhas comuns (ex.: Excel).
- **FR-004**: O sistema MUST desabilitar a ação de exportação quando não houver talhões para exportar.

### Key Entities

- **Relatório de Talhões (CSV)**: arquivo tabular com os talhões filtrados no momento da exportação e seus atributos-chave.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um usuário gera o arquivo exportado em menos de 3 segundos após clicar em exportar.
- **SC-002**: 100% dos arquivos exportados refletem exatamente o filtro aplicado no momento da exportação (nenhum talhão a mais ou a menos).
- **SC-003**: 0% dos arquivos exportados apresentam corrupção de acentuação ao abrir em planilhas comuns.

## Assumptions

- Esta feature consome dados já calculados pelo Dashboard de Plantio (011); não introduz novo cálculo de status ou umidade.
- Formatos de exportação além de CSV (ex.: PDF, Excel nativo) estão fora de escopo desta feature.
