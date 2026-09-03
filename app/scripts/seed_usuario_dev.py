"""Cria/atualiza um usuário de desenvolvimento local para testar o login do
frontend (`Agro_Tec_app`) contra a API real. Não há endpoint de cadastro de
usuário (RD009/HU-01 não previu self-signup) — usuários são provisionados
diretamente no banco. Uso local apenas, nunca rodar em produção.

    python -m app.scripts.seed_usuario_dev [email] [senha] [papel]

Padrão: produtor@agroclima.dev / agroclima123 / PRODUTOR_RURAL
"""

import asyncio
import sys

from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert as pg_insert

from app.core.security import hash_senha
from app.db.models.usuario import Papel, Usuario
from app.db.session import AsyncSessionLocal

EMAIL_PADRAO = "produtor@agroclima.dev"
SENHA_PADRAO = "agroclima123"
PAPEL_PADRAO = Papel.PRODUTOR_RURAL


async def seed(email: str, senha: str, papel: Papel) -> None:
    senha_hash = await hash_senha(senha)
    async with AsyncSessionLocal() as db:
        stmt = (
            pg_insert(Usuario)
            .values(email=email, senha_hash=senha_hash, papel=papel)
            .on_conflict_do_update(index_elements=["email"], set_={"senha_hash": senha_hash, "papel": papel})
        )
        await db.execute(stmt)
        await db.commit()

        usuario = (await db.execute(select(Usuario).where(Usuario.email == email))).scalar_one()
        print(f"Usuário pronto: {usuario.email} (id={usuario.id}, papel={usuario.papel.value})")


if __name__ == "__main__":
    email = sys.argv[1] if len(sys.argv) > 1 else EMAIL_PADRAO
    senha = sys.argv[2] if len(sys.argv) > 2 else SENHA_PADRAO
    papel = Papel(sys.argv[3]) if len(sys.argv) > 3 else PAPEL_PADRAO
    asyncio.run(seed(email, senha, papel))
