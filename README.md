# AgroClima Pará — API

Backend (FastAPI) do projeto AgroClima Pará. Ver a documentação completa
(Constituição, Convenção Técnica, requisitos, HUs) em `Agro_Tec_documentacao`.

## Variáveis de ambiente

As variáveis de ambiente deste serviço (`.env`) são versionadas em
`Agro_Tec_infra` como `api.env.example` — copie de lá para `.env` nesta pasta:

```bash
cp ../Agro_Tec_infra/api.env.example .env
```

Nunca commitar o `.env` real (com segredos) neste repositório.

## Rodando localmente

```bash
uv venv .venv && source .venv/bin/activate
uv pip install -r requirements.txt
alembic upgrade head
uvicorn app.main:app --reload
```

## Testes

```bash
pytest
```

Testes de integração (`tests/integration/`) exigem um PostgreSQL com a extensão
PostGIS habilitada; se indisponível, são pulados automaticamente.
