# Data Model: Integração com Open-Meteo

Esta feature não introduz tabela persistente própria — os dados obtidos alimentam diretamente outras features (Balanço Hídrico, Ingestão) que decidem o que persistir. Estruturas de transporte (não persistidas):

## PrevisaoClimatica (objeto de transporte, não tabela)

| Campo | Tipo | Descrição |
|---|---|---|
| `latitude`, `longitude` | float | coordenada consultada |
| `vento_10m_kmh` | float | vento horário a 10m |
| `vento_100m_kmh` | float | vento horário a 100m |
| `evapotranspiracao_mm` | float | ET0 diária (FAO-56 Penman-Monteith, calculada pela fonte) |
| `umidade_solo_0_7cm` | float | fração de umidade da camada superficial |
| `umidade_solo_outras_camadas` | array | demais profundidades retornadas pela fonte |
| `precipitacao_prevista_mm` | float | chuva prevista para as próximas 24h — adicionado ao consumir esta feature na 011 (RN004 exige "chuva prevista", não a precipitação já medida) |
| `obtido_em_utc` | timestamp | instante da consulta, para controle de cache/staleness |
