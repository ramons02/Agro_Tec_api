-- Spec 024 (HU-20), fase 1: grupos (tenants) e a migracao dos dados existentes para um grupo inicial.
-- Nao muda comportamento: as colunas novas ficam anulaveis e nenhuma regra de autorizacao usa o grupo ainda.

CREATE TABLE grupos (
    id UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    -- Admin (ou, no grupo inicial, o gestor atual) a frente do grupo. Sem FK: usuarios.grupo_id aponta para
    -- grupos e a validacao deste lado fica no servico.
    admin_id UUID NOT NULL,
    -- Link de cadastro do grupo: so o hash do codigo e guardado.
    codigo_cadastro_hash VARCHAR(64) NOT NULL,
    codigo_cadastro_gerado_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ix_grupos_nome ON grupos (lower(nome));
CREATE UNIQUE INDEX ix_grupos_codigo_cadastro ON grupos (codigo_cadastro_hash);

ALTER TABLE usuarios ADD COLUMN grupo_id UUID REFERENCES grupos (id);
CREATE INDEX ix_usuarios_grupo ON usuarios (grupo_id);

-- O agronomo pode atender mais de um grupo.
CREATE TABLE agronomo_grupos (
    agronomo_id UUID NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    grupo_id UUID NOT NULL REFERENCES grupos (id),
    criado_em TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (agronomo_id, grupo_id)
);

CREATE INDEX ix_agronomo_grupos_grupo ON agronomo_grupos (grupo_id);

-- Copia do grupo do dono, para filtrar propriedades (e, por elas, talhoes) em consulta simples.
ALTER TABLE propriedades ADD COLUMN grupo_id UUID REFERENCES grupos (id);
CREATE INDEX ix_propriedades_grupo ON propriedades (grupo_id);

-- Hoje todo convite cria um gestor; a fase 3 passa a convidar so Masters.
ALTER TABLE convites_gestor ADD COLUMN papel_destino VARCHAR(30) NOT NULL DEFAULT 'GESTOR_TECNOLOGIA';

-- Acoes de gestao de grupo na auditoria, e o grupo afetado quando a acao e sobre um grupo.
ALTER TABLE auditoria_contas ADD COLUMN alvo_grupo_id UUID;
ALTER TABLE auditoria_contas DROP CONSTRAINT ck_auditoria_contas_acao;
ALTER TABLE auditoria_contas ADD CONSTRAINT ck_auditoria_contas_acao CHECK (acao IN
    ('CONFIRMAR', 'RECUSAR', 'CRIAR', 'SUSPENDER', 'REATIVAR', 'REDEFINIR_SENHA', 'EDITAR', 'SENHA_ESQUECIDA',
     'CRIAR_ADMIN', 'MOVER_GRUPO', 'REATRIBUIR_GRUPO', 'REMOVER_DO_GRUPO', 'NOVO_LINK_GRUPO'));

-- Migracao dos dados: um grupo inicial sob o gestor mais antigo. Sem gestor (banco novo ou de teste),
-- nao cria nada. O codigo do link nasce aleatorio e desconhecido: o link fica desligado ate o Admin gerar um.
INSERT INTO grupos (id, nome, admin_id, codigo_cadastro_hash, codigo_cadastro_gerado_em, criado_em)
SELECT gen_random_uuid(),
       'Grupo inicial',
       u.id,
       encode(sha256(convert_to(gen_random_uuid()::text || clock_timestamp()::text, 'UTF8')), 'hex'),
       now(),
       now()
FROM usuarios u
WHERE u.papel = 'GESTOR_TECNOLOGIA'
ORDER BY u.criado_em, u.id
LIMIT 1;

-- Produtores e gestores ja ativos entram no grupo inicial. Cadastros pendentes ficam sem grupo (o Master
-- atribui ao confirmar, na fase 4), como a spec manda.
UPDATE usuarios
SET grupo_id = (SELECT id FROM grupos ORDER BY criado_em, id LIMIT 1)
WHERE papel IN ('PRODUTOR_RURAL', 'GESTOR_TECNOLOGIA')
  AND status <> 'PENDENTE'
  AND EXISTS (SELECT 1 FROM grupos);

INSERT INTO agronomo_grupos (agronomo_id, grupo_id, criado_em)
SELECT u.id, (SELECT id FROM grupos ORDER BY criado_em, id LIMIT 1), now()
FROM usuarios u
WHERE u.papel = 'AGRONOMO'
  AND u.status <> 'PENDENTE'
  AND EXISTS (SELECT 1 FROM grupos);

UPDATE propriedades p
SET grupo_id = u.grupo_id
FROM usuarios u
WHERE u.id = p.proprietario_id;
