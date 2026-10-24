-- Spec 024 (HU-20), fase 2: o gestor que gere o grupo inicial vira o Admin Master, e o preenchimento do grupo
-- e completado para quem foi criado depois da V19. Roda junto com o filtro de grupo da autorizacao: antes dele,
-- promover o gestor tiraria dele o acesso (todo o codigo comparava com GESTOR_TECNOLOGIA).

-- 1. Promocao: so a conta que e o Admin do grupo inicial (a do gestor mais antigo). Banco sem grupo nao muda nada.
UPDATE usuarios
SET papel = 'ADMIN_MASTER'::papel_usuario
WHERE papel = 'GESTOR_TECNOLOGIA'
  AND id = (SELECT admin_id FROM grupos ORDER BY criado_em, id LIMIT 1);

-- 2. Contas ativas criadas depois da V19 entram no grupo inicial; pendentes seguem sem grupo.
UPDATE usuarios
SET grupo_id = (SELECT id FROM grupos ORDER BY criado_em, id LIMIT 1)
WHERE papel IN ('PRODUTOR_RURAL', 'GESTOR_TECNOLOGIA', 'ADMIN_MASTER')
  AND status <> 'PENDENTE'
  AND grupo_id IS NULL
  AND EXISTS (SELECT 1 FROM grupos);

INSERT INTO agronomo_grupos (agronomo_id, grupo_id, criado_em)
SELECT u.id, (SELECT id FROM grupos ORDER BY criado_em, id LIMIT 1), now()
FROM usuarios u
WHERE u.papel = 'AGRONOMO'
  AND u.status <> 'PENDENTE'
  AND EXISTS (SELECT 1 FROM grupos)
  AND NOT EXISTS (SELECT 1 FROM agronomo_grupos a WHERE a.agronomo_id = u.id);

UPDATE propriedades p
SET grupo_id = u.grupo_id
FROM usuarios u
WHERE u.id = p.proprietario_id
  AND p.grupo_id IS NULL;

-- 3. Restricao segura: o agronomo nunca tem grupo proprio (usa agronomo_grupos). As demais regras (produtor e
-- Admin ativos tem grupo) ficam no servico, para nao rejeitar contas legadas nem bancos sem grupo.
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_agronomo_sem_grupo CHECK (papel <> 'AGRONOMO' OR grupo_id IS NULL);
