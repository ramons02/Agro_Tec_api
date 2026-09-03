# Phase 0 Research: Exportação de Relatório de Talhões

## Client-side vs. endpoint dedicado

- **Decision**: endpoint dedicado no backend (`/dashboard/plantio/exportar.csv`), não geração client-side como no protótipo.
- **Rationale**: o protótipo gera o CSV no cliente porque todos os dados mockados já estavam na tela; no backend real, o Dashboard (feature 011) é paginado (RNF017) — gerar o CSV no cliente exportaria só a página visível, não "todos os talhões atualmente filtrados" como exige FR-001. Um endpoint que aplica o filtro sem paginação resolve isso corretamente.
- **Alternatives considered**: carregar todas as páginas no frontend antes de exportar — mais requisições, mais lento, e duplica no cliente uma lógica de filtro que já existe no backend.

## Encoding

- **Decision**: escrever o BOM UTF-8 (`﻿`) no início do corpo da resposta, `Content-Type: text/csv; charset=utf-8`.
- **Rationale**: é o requisito explícito (FR-003) para abrir corretamente no Excel em português sem corromper acentuação.
