# Phase 0 Research: Parametrização Automática de Solo via SoilGrids

## Limiares de classificação de textura

- **Decision**: aplicar o triângulo textural padrão (USDA/Embrapa) sobre as frações de argila/areia/silte retornadas para derivar `ARGILOSO`/`ARENOSO`/`MISTO`, conforme já usado como referência em `escopo/calculos-geo-metero.md`.
- **Rationale**: é o padrão científico já citado na documentação do projeto; evita inventar limiares arbitrários.
- **Alternatives considered**: limiares simplificados por porcentagem única de argila — descartado por divergir da prática agronômica padrão referenciada no Escopo.

## Profundidade de raízes ($z$) para a fórmula da CAD

- **Decision**: valor padrão de 200mm quando não informado — é exatamente o exemplo dado em `escopo/calculos-geo-metero.md` §4A ("z: Profundidade das raízes da cultura, ex: 200mm para fase inicial de germinação").
- **Rationale**: usar o próprio valor de exemplo do documento oficial evita introduzir um número arbitrário divergente sem necessidade; mantém rastreabilidade direta à fonte.
- **Alternatives considered**: 300mm (chegou a ser cogitado por analogia à camada de germinação 0-30cm, mas não tem base no documento oficial — descartado em favor do exemplo já dado); exigir que o usuário informe a cultura/profundidade — adiado para uma iteração futura, fora do escopo do MVP.

## Cobertura geográfica do SoilGrids

- **Decision**: tratar ausência de cobertura como resultado válido (`tipo_solo = null`), nunca como erro que impede salvar o talhão.
- **Rationale**: requisito explícito (FR-006) e RN016 (talhão fora de área esperada é aceito com confirmação, nunca bloqueado).
