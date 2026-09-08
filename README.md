# AgroClima Pará — API

Backend (Spring Boot / Java 21) do projeto AgroClima Pará. Só código aqui — a
documentação completa (Constituição, Convenção Técnica, requisitos, HUs) e o
planejamento Spec Kit deste backend (`specs/`, `.specify/`) moram em
`Agro_Tec_documentacao`.

Migrado de FastAPI/Python pra Spring Boot/Java em 2026-09-08 (plano em
`/home/ramon/.claude/plans/imperative-plotting-flamingo.md`, 9 fases) — histórico
completo do backend em Python disponível via `git log`/`git show` em commits
anteriores a esta migração.

## Variáveis de ambiente

As variáveis de ambiente deste serviço moram fisicamente em `Agro_Tec_infra`
(`api.env`, nunca commitado) — este repositório aponta pra lá por link
simbólico:

```bash
ln -s ../Agro_Tec_infra/api.env .env
```

Nunca commitar o `.env` real (com segredos) neste repositório.

## Rodando localmente

```bash
mvn spring-boot:run
```

Requer `DATABASE_URL` no formato JDBC (`jdbc:postgresql://host:5432/banco`),
`DATABASE_USER` e `DATABASE_PASSWORD` separados (não embutidos na URL).

## Testes

```bash
mvn test
```

Testes de integração (`*IT.java`) rodam contra um PostgreSQL real com PostGIS
habilitado (schema próprio, isolado do schema de desenvolvimento manual).
