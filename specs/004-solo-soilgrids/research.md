# Phase 0 Research: Parametrização Automática de Solo via SoilGrids

## Limiares de classificação de textura

- **Decision**: usar limiares simplificados (argila ≥35% → `ARGILOSO`; areia ≥70% e argila <15% → `ARENOSO`; caso contrário → `MISTO`), em vez do triângulo textural USDA/Embrapa completo (12 classes).
- **Rationale**: o domínio do projeto (RD005) só precisa de 3 categorias, não das 12 classes texturais completas; implementar a geometria do triângulo completo para depois colapsar em 3 buckets seria complexidade desnecessária (YAGNI) sem mudar o resultado de negócio. Os limiares usados (35% argila, 70% areia) são os pontos de corte reais do triângulo USDA para as classes "clay" e "sand", preservando a base científica sem a geometria completa.
- **Alternatives considered**: triângulo textural completo (12 classes) com mapeamento posterior para 3 categorias — descartado por adicionar complexidade geométrica (equações de retas do triângulo) sem alterar a decisão final de negócio.

## Profundidade de raízes ($z$) para a fórmula da CAD

- **Decision**: valor padrão de 200mm quando não informado — é exatamente o exemplo dado em `escopo/calculos-geo-metero.md` §4A ("z: Profundidade das raízes da cultura, ex: 200mm para fase inicial de germinação").
- **Rationale**: usar o próprio valor de exemplo do documento oficial evita introduzir um número arbitrário divergente sem necessidade; mantém rastreabilidade direta à fonte.
- **Alternatives considered**: 300mm (chegou a ser cogitado por analogia à camada de germinação 0-30cm, mas não tem base no documento oficial — descartado em favor do exemplo já dado); exigir que o usuário informe a cultura/profundidade — adiado para uma iteração futura, fora do escopo do MVP.

## Cobertura geográfica do SoilGrids

- **Decision**: tratar ausência de cobertura como resultado válido (`tipo_solo = null`), nunca como erro que impede salvar o talhão.
- **Rationale**: requisito explícito (FR-006) e RN016 (talhão fora de área esperada é aceito com confirmação, nunca bloqueado).
