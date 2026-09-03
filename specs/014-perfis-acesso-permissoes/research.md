# Phase 0 Research: Perfis de Acesso e Permissões

## Modelo de autorização

- **Decision**: dependências FastAPI (`Depends`) que recebem o usuário autenticado (feature 001) e o `propriedade_id`/`talhao_id` da rota, verificando: `GESTOR_TECNOLOGIA` → sempre permitido; `PRODUTOR_RURAL` → permitido só se `proprietario_id == usuario.id`; `AGRONOMO` → permitido para leitura só se existir vínculo com `estado = ACEITO`, nunca para escrita.
- **Rationale**: centraliza a regra de autorização em um único ponto reutilizável, evitando duplicação e divergência entre endpoints (risco real quando a mesma regra é copiada em vários lugares).
- **Alternatives considered**: checagem manual dentro de cada endpoint — descartado por risco de inconsistência (um endpoint esquecido sem a checagem).

## Estado do vínculo

- **Decision**: enum `CONVIDADO` / `ACEITO` / `REVOGADO` na tabela de vínculo; acesso de leitura só é concedido com estado `ACEITO`.
- **Rationale**: modela exatamente o fluxo de convite→aceite exigido pelo FR-005, e permite revogação futura sem apagar o histórico do vínculo.

## Erro 403 vs. 404

- **Decision**: qualquer tentativa de acesso a um recurso que existe mas não é permitido retorna 403 com mensagem explicando o motivo; 404 é reservado exclusivamente para recurso que realmente não existe.
- **Rationale**: requisito explícito (FR-006) — evita confundir o usuário sobre se o erro é de permissão ou de dado inexistente.
