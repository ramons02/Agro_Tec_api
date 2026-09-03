# Data Model: Parametrização Automática de Solo via SoilGrids

## Extensão de Talhao (ver também feature 005)

| Campo | Tipo | Regras |
|---|---|---|
| `tipo_solo` | enum, nullable | `ARGILOSO` \| `ARENOSO` \| `MISTO` \| `null` (sem cobertura) |
| `fracao_argila_pct` | numeric, nullable | |
| `fracao_areia_pct` | numeric, nullable | |
| `fracao_silte_pct` | numeric, nullable | |
| `materia_organica_pct` | numeric, nullable | |
| `capacidade_agua_disponivel_mm` | numeric, nullable | CAD calculada (RN020) |

## Fórmula (referência, não redefinida aqui)

$CAD = (CC - PMP) \times \rho_s \times z$ — CC/PMP em % de retenção obtidos do SoilGrids, $\rho_s$ em g/cm³, $z$ = profundidade de raízes em mm (ver research.md para o valor padrão assumido).
