from fastapi import APIRouter

from app.api.v1.endpoints import auth, clima, propriedades, talhoes

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(auth.router)
api_router.include_router(propriedades.router)
api_router.include_router(talhoes.router)
api_router.include_router(clima.router)
