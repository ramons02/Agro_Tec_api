# Phase 0 Research: Cadastro Territorial de Propriedades e Talhões

## Biblioteca de parsing geográfico

- **Decision**: Shapely para geometria em memória e validação; GeoPandas (ou `fiona`/`pyshp` diretamente) para leitura de Shapefile; `pykml`/parsing XML simples para KML; `json` nativo para GeoJSON.
- **Rationale**: conjunto padrão do ecossistema geoespacial Python, já citado na Escopo Técnico (GeoPandas, Shapely).
- **Alternatives considered**: rolar parser próprio para cada formato — rejeitado por reinventar um problema já resolvido por bibliotecas maduras.

## Limiar de sobreposição

- **Decision**: usar `ST_Overlaps` combinado com `ST_Area(ST_Intersection(...)) > 10` (m², em projeção métrica local) para decidir bloqueio dentro da mesma propriedade.
- **Rationale**: valor já validado com o dono do produto (RN015).
- **Alternatives considered**: bloquear qualquer sobreposição, mesmo mínima — rejeitado por gerar falso-positivo em polígonos desenhados manualmente com pequena imprecisão de borda.

## Bounding box aproximada do Pará

- **Decision**: retângulo envolvente aproximado (latitude/longitude) cobrindo o estado do Pará, usado apenas como heurística de confirmação (RN016), não como validação rígida.
- **Rationale**: já é a decisão validada — talhão fora da caixa é aceito mediante confirmação, nunca bloqueado.

## Formato de erro de validação geométrica

- **Decision**: reaproveitar o envelope de erro padrão (`status: erro`, `codigo`, `mensagem`) com `codigo: 422` para geometria inválida e um código de aplicação específico (ex.: `409` ou um payload com `tipo: "SOBREPOSICAO"`) para os casos de negócio (sobreposição/fora do Pará), distinguindo erro de validação de decisão de negócio que pode ser contornada com confirmação.
