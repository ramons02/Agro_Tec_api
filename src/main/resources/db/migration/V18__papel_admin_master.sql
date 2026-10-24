-- Spec 024 (HU-20), fase 1: papel do dono da plataforma. O Postgres nao deixa usar um valor de enum na
-- mesma transacao em que ele foi criado, e o Flyway roda cada migration numa transacao: por isso este
-- comando fica sozinho aqui e a V19 (e as seguintes) podem usar o valor.
-- Nenhuma conta recebe o papel nesta fase; a promocao do gestor atual a ADMIN_MASTER vem junto com a
-- autorizacao por grupo (fase 2), quando o comportamento do papel realmente muda.
ALTER TYPE papel_usuario ADD VALUE IF NOT EXISTS 'ADMIN_MASTER';
