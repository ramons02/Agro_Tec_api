import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, Response
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.response import AppError, envelope_sucesso
from app.core.security import UsuarioAutenticado, get_current_user
from app.db.models.talhao import Talhao
from app.db.session import get_db
from app.services.clima_tempo_real_service import obter_clima_atual

router = APIRouter(prefix="/clima", tags=["clima"])


@router.get("/atual")
async def clima_atual(
    response: Response,
    talhao_id: uuid.UUID,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
    _t: int | None = None,
) -> dict:
    """RN008/RN017/Princípio III — nunca serve dado >30min sem tentar atualizar
    (contrato em `contracts/clima-atual.md`). `_t` é usado só para bypass de
    cache do cliente/proxy; o backend reforça isso via headers abaixo."""
    response.headers["Cache-Control"] = "no-cache, no-store, must-revalidate"
    response.headers["Pragma"] = "no-cache"

    talhao = await db.get(Talhao, talhao_id)
    if talhao is None:
        raise AppError(404, "Talhão não encontrado.")

    resultado = await obter_clima_atual(db, talhao)
    if resultado is None:
        raise AppError(404, "Nenhuma medição disponível para este talhão ainda.")

    return envelope_sucesso(
        {
            "estacao": resultado.estacao_codigo,
            "chuva_mm": resultado.chuva_mm,
            "vento_kmh": resultado.vento_kmh,
            "rajada_kmh": resultado.rajada_kmh,
            "fonte_dados": resultado.fonte_dados.value,
            "medido_em_utc": resultado.medido_em_utc.isoformat(),
        }
    )
