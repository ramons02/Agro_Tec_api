-- Spec 024 (HU-20), fase 3: o convite passa a servir so para convidar Masters; Admins nascem por criacao direta
-- do Master (com senha temporaria). Convites de gestor ainda nao usados deixam de valer: nao ha como transforma-los
-- em Master sem o Master que os criou saber.
ALTER TABLE convites_gestor ALTER COLUMN papel_destino SET DEFAULT 'ADMIN_MASTER';

UPDATE convites_gestor
SET expira_em = now()
WHERE usado_em IS NULL
  AND papel_destino = 'GESTOR_TECNOLOGIA'
  AND expira_em > now();
