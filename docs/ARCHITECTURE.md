# Architecture

## The idea in one picture

```
 Employee ──► any UI (web / Teams / ServiceNow / Jira) ──► REST API /api/v1
                                                              │
                         ┌────────────────────────────────────▼───────────────────────┐
                         │ backend (Spring Boot)                                       │
                         │                                                             │
                         │  api ──► service ──► core.pipeline ──► AiClient (port)      │
                         │                         │                 │                 │
                         │                         │            ai.OllamaAiClient ─────┼──► Ollama (Llama)
                         │                         ▼                                   │
                         │                     persistence ─────────────────────────── ┼──► PostgreSQL
                         └─────────────────────────────────────────────────────────────┘        │
                                                                                                 ▼
                                                                         reporting.v_* views ──► Power BI
```

## Packages (backend)

| Package | Responsibility | Knows about |
|---|---|---|
| `core` | Business logic: the pipeline, scoring, rendering, templates. **No framework imports.** | nothing else |
| `core.pipeline` | `EvaluationPipeline`: extract → merge → decide → score → render | `AiClient` interface |
| `ai` | `OllamaAiClient`, prompt, strict JSON parsing | Ollama HTTP API |
| `template` | Loads YAML templates | SnakeYAML |
| `persistence` | JPA entities + repositories | PostgreSQL |
| `service` | Orchestration: one submission's lifecycle, transactions | all of the above |
| `api` | REST controllers, request validation, error mapping (RFC 9457) | `service` |
| `config` | Wiring, CORS, API key filter, typed properties | everything |

Rule: dependencies point inwards. `core` never imports `ai`, `persistence` or Spring.

## Lifecycle of a submission

```
POST /justifications {templateId, text}
        │
        ▼
  model extracts fields ──► code: required field missing?
        │                         │yes                         │no
        │                         ▼                            ▼
        │                 round < max?  ──no──►  INCOMPLETE  COMPLETE
        │                         │yes             (rendered,   (rendered)
        │                         ▼                 gaps shown)
        │                 NEEDS_INFO + ≤3 questions
        │                         │
        └── POST /justifications/{id}/answers {answers}  (answers are trusted as-is; earlier values kept)
```

Each round is saved in `submission_round` (what was missing, what was asked), which powers the analytics.

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/justifications` | Submit text. `source` + `externalRef` optional (idempotent per source system). |
| POST | `/api/v1/justifications/{id}/answers` | Answer the questions: `{"answers": {"port_protocol": "TCP 443"}}` |
| GET | `/api/v1/justifications/{id}` | Current state |
| GET | `/api/v1/templates`, `/api/v1/templates/{id}` | Available request types and their fields |
| GET | `/api/v1/stats` | Quick numbers (full analytics: Power BI) |

Interactive docs: `http://localhost:8080/swagger-ui.html`. Errors are `application/problem+json`:
400 invalid input, 404 unknown template/submission, 409 submission closed or concurrent update,
503 model unavailable.

## Data model

```
submission                         submission_round
──────────                         ────────────────
id (uuid) PK                ◄──┐   id PK
template_id, source             └── submission_id FK
external_ref (unique per source)    round_no
status NEEDS_INFO|COMPLETE|INCOMPLETE  input_text
round_count                         missing_fields  jsonb ["duration", ...]
quality_score, completeness, clarity  questions     jsonb {"duration": "..."}
standardized_text                   clarity
extracted_fields  jsonb             created_at
raw_text
created_at, updated_at, completed_at
version (optimistic lock)
```

Reporting views (read by Power BI): see [POWERBI.md](POWERBI.md).

## Extension points

| You want to… | Do this |
|---|---|
| Support a new request type | Add a YAML template (no code). See `templates/README.md`. |
| Use another model or provider | Implement `AiClient`, register it as a bean. |
| Plug into ServiceNow / Jira / Teams | Call the REST API from that system with `source` + `externalRef`. A connector is just an adapter that maps its events to these 2 calls. See `docs/BACKLOG.md`. |
| Build a new report | Add a view in a new Flyway migration under `reporting`; grant follows automatically. |

## Frontend

`frontend/` is a separate React app with its own build and container. It only calls `/api/v1/*`. In Docker,
nginx serves the static files and proxies `/api` to the backend. Arabic (RTL) and English.
