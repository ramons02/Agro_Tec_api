from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.v1.router import api_router
from app.core.config import get_settings
from app.core.response import AppError, app_error_handler, envelope_erro
from app.core.scheduler import iniciar_scheduler, parar_scheduler


@asynccontextmanager
async def lifespan(app: FastAPI):
    iniciar_scheduler()
    yield
    parar_scheduler()


app = FastAPI(title="AgroClima Pará API", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=get_settings().cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
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
