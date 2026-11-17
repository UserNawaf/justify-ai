# Justify AI

AI-assisted standardisation of request justifications (firewall rules first).

Today an employee writes a free-text justification, the engineer handling the ticket can't act on it, and the
two go back and forth on Teams or email asking "what do you mean?". Justify AI sits in between:

1. the employee writes the justification their own way (Arabic or English);
2. a local Llama model reads it; the code works out what is missing and asks **at most 3 short questions**;
3. the engineer receives a **fixed-format** justification with a quality score;
4. every request and every round is stored, so Power BI can show what people forget and whether quality improves.

It is a standalone service with a REST API, so it can be added to any workplace tool (web form, ServiceNow,
Jira, Teams) through a thin adapter.

```
Request type: Firewall rule request
Business purpose: Payroll app must call the bank's payment API for monthly salaries
Source: payroll-app-01 (10.10.5.20)
Destination: api.bank.example (203.0.113.10)
Port / protocol: TCP 443
Duration: Permanent
Impact if rejected: Salaries cannot be paid on time
Data classification: Not provided
```

## Stack

Java 21 · Spring Boot 3.5 · PostgreSQL 16 (JSONB, Flyway) · Llama via Ollama (runs locally, data stays on the
machine) · React 19 + Vite (isolated frontend) · Docker Compose · JUnit 5, Mockito, Testcontainers · GitHub Actions

## Run it

Requires Docker.

```bash
cp .env.example .env          # set the passwords
docker compose up --build     # first run downloads the model (~5 GB), be patient
```

| | URL |
|---|---|
| Web app | http://localhost:8081 |
| API + Swagger UI | http://localhost:8080/swagger-ui.html |
| PostgreSQL (Power BI) | localhost:5432, database `justify`, user `powerbi_reader` |

### Try the API

```bash
curl -s localhost:8080/api/v1/justifications -H 'Content-Type: application/json' \
  -d '{"templateId":"firewall-rule","text":"Please open port 443 from the payroll server to the bank, urgent"}'
# -> status NEEDS_INFO + questions, e.g. "Is this rule permanent or temporary?"

curl -s localhost:8080/api/v1/justifications/<id>/answers -H 'Content-Type: application/json' \
  -d '{"answers":{"duration":"Permanent","impact_if_rejected":"Salaries are paid late"}}'
# -> status COMPLETE + standardizedText + qualityScore
```

### Develop without Docker

```bash
# PostgreSQL on localhost:5432 (db/user/password: justify) and Ollama on localhost:11434
ollama pull llama3.1:8b
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev      # http://localhost:5173, proxies /api to :8080
```

## Repository

```
backend/     Spring Boot service (see docs/ARCHITECTURE.md)
frontend/    React app, isolated: talks to the API only, own container
db/init/     one-time database setup (Power BI read-only login)
templates/   extra request-type templates, loaded at start-up (no code needed)
docs/        ARCHITECTURE · TESTING · POWERBI · BACKLOG (Jira-ready)
DECISIONS.md every decision taken while building, and why
```

## Configuration

Environment variables (defaults in `backend/src/main/resources/application.yml`):

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | local `justify` DB | Database |
| `OLLAMA_URL`, `OLLAMA_MODEL` | `http://localhost:11434`, `llama3.1:8b` | Model |
| `TEMPLATES_DIR` | empty | Folder with extra `*.yml` templates |
| `APP_API_KEY` | empty (off) | Shared secret for server-to-server callers (`X-API-Key`) |
| `CORS_ORIGINS` | `http://localhost:5173` | Only needed if a browser calls the API from another origin |
| `POWERBI_READER_PASSWORD` | empty | Creates the read-only Power BI login on first start |

## Status of this MVP

Built end to end by an AI agent working unattended; every decision is in [DECISIONS.md](DECISIONS.md).

**Verified while building**
- Core logic (pipeline, scoring, rendering, template and model-reply parsing) compiled and exercised with a smoke run.
- Flyway migrations applied on PostgreSQL 16; the reporting views return correct numbers; the Power BI login can
  read the views and is refused on the raw tables.
- Frontend type-checks and builds for production.

**Not yet run**: the full Spring Boot build and JUnit suite. The build sandbox could not reach Maven Central,
so `mvn verify` has not been executed yet. Run it first (CI does it automatically on the first push) and fix
anything it reports; likely candidates are small API mismatches, not design issues.

**Before using real company data**: it runs a local model, but get the security team's approval anyway,
especially for storing IP addresses and hostnames.

Next steps: [docs/BACKLOG.md](docs/BACKLOG.md).
