from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.v1.router import api_router
from app.core.response import AppError, app_error_handler, envelope_erro

app = FastAPI(title="AgroClima Pará API")
app.include_router(api_router)
app.add_exception_handler(AppError, app_error_handler)


@app.exception_handler(RequestValidationError)
async def validation_error_handler(request, exc: RequestValidationError) -> JSONResponse:
    return JSONResponse(
        status_code=422,
        content=envelope_erro(422, "Dados de entrada inválidos.", exc.errors()),
    )


@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}
