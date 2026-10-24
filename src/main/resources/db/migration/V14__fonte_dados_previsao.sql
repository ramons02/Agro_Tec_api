-- Medicao vinda do fallback Open-Meteo (modelo de previsao) deixa de ser gravada como AO_VIVO,
-- rotulo que passa a significar so medicao real de estacao INMET.
ALTER TYPE fonte_dados_medicao ADD VALUE IF NOT EXISTS 'PREVISAO';
