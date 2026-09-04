from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str
    jwt_secret: str
    jwt_algorithm: str = "HS256"
    jwt_expiration_hours: int = 24
    cors_origins: list[str] = ["http://localhost:5180"]
    cors_origin_regex: str | None = None

    # Feature 013 — recuperação de senha. SMTP_* ausente (padrão) faz o envio
    # cair para o log em vez de uma tentativa real de conexão (Princípio II —
    # custo zero; nenhum provedor SMTP gratuito foi contratado ainda).
    smtp_host: str | None = None
    smtp_port: int = 587
    smtp_user: str | None = None
    smtp_password: str | None = None
    smtp_from: str | None = None
    frontend_base_url: str = "http://localhost:5180"


@lru_cache
def get_settings() -> Settings:
    return Settings()
