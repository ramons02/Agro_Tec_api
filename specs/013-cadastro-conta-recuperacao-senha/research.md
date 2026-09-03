# Phase 0 Research: Cadastro de Conta e Recuperação de Senha

## Hash de senha bloqueante em contexto assíncrono

- **Decision**: executar `bcrypt.hashpw`/`passlib` em um `run_in_executor` (thread pool), nunca diretamente na coroutine.
- **Rationale**: bcrypt é propositalmente lento (CPU-bound); rodá-lo direto no event loop bloquearia todas as outras requisições concorrentes, violando o espírito do Princípio I mesmo que a função "pareça" chamada de dentro de um `async def`.
- **Alternatives considered**: usar um algoritmo mais rápido — rejeitado, pois o custo computacional é a própria defesa de segurança do bcrypt contra força bruta.

## Provedor de envio de email

- **Decision**: usar um provedor transacional com tier gratuito (ex.: um serviço SMTP gratuito de baixo volume) via `aiosmtplib`, mantendo a implementação por trás de uma interface própria (`EmailService`) para trocar de provedor sem afetar o restante do código.
- **Rationale**: mantém o Princípio II (custo zero) enquanto atende ao requisito de envio de email; a interface própria evita acoplamento a um provedor específico.
- **Alternatives considered**: provedor pago desde já — descartado por violar a restrição de custo zero sem necessidade comprovada de volume que o justifique.

## Formato e armazenamento do token de recuperação

- **Decision**: token opaco (UUID aleatório ou hash de valor aleatório), armazenado com `usuario_id`, `expira_em` (1h) e `usado_em` (nullable); nunca o próprio JWT de sessão.
- **Rationale**: separar o token de recuperação do token de sessão (feature 001) evita que um vazamento de um comprometa o outro, e permite marcar uso único de forma simples (campo `usado_em`).
