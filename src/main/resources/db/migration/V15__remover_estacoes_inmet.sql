-- Remocao do INMET: o clima agora vem de modelo (Open-Meteo, com MET Norway de reserva), por talhao,
-- sem estacoes. As rotas de medicao do INMET nao devolvem dado, e estas tabelas so guardavam esse
-- historico (leituras de estacao e o fallback antigo). IRREVERSIVEL: faca backup antes se quiser guarda-lo.
DROP TABLE IF EXISTS medicoes_clima;
DROP TABLE IF EXISTS estacoes_inmet;
DROP TYPE IF EXISTS fonte_dados_medicao;
