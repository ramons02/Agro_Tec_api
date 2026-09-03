from datetime import UTC, datetime
from typing import Any

from fastapi import Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel


class AppError(Exception):
    """Erro de aplicação traduzido para o envelope de erro padrão (Convenção Técnica §5.2)."""

    def __init__(self, codigo: int, mensagem: str, detalhes: Any = None) -> None:
        self.codigo = codigo
        self.mensagem = mensagem
        self.detalhes = detalhes
        super().__init__(mensagem)


def envelope_sucesso(dados: Any) -> dict:
    return {
        "status": "sucesso",
        "data_consulta_utc": datetime.now(UTC).isoformat(),
        "dados": dados,
    }


def envelope_erro(codigo: int, mensagem: str, detalhes: Any = None) -> dict:
    return {
        "status": "erro",
        "codigo": codigo,
        "mensagem": mensagem,
        "detalhes": detalhes,
    }


async def app_error_handler(request: Request, exc: AppError) -> JSONResponse:
    return JSONResponse(
        status_code=exc.codigo,
        content=envelope_erro(exc.codigo, exc.mensagem, exc.detalhes),
    )


class SucessoResponse(BaseModel):
    status: str = "sucesso"
    data_consulta_utc: str
    dados: Any
