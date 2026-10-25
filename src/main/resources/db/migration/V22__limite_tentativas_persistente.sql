-- Limites de tentativas (login, troca e recuperacao de senha, cadastro publico) deixam de ser so em memoria:
-- sobrevivem a reinicio e valem para mais de uma instancia. A chave (e-mail ou IP) fica so como hash SHA-256,
-- nunca em texto. Linhas vencidas sao apagadas por um job diario.
CREATE TABLE limite_tentativas (
    escopo      VARCHAR(30)  NOT NULL,
    chave_hash  CHAR(64)     NOT NULL,
    contagem    INTEGER      NOT NULL,
    expira_em   TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (escopo, chave_hash)
);

CREATE INDEX idx_limite_tentativas_expira_em ON limite_tentativas (expira_em);
