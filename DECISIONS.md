# Decisions

Every choice made while building the MVP without a human in the loop, and why. Disagree with one? Change it:
each is small and isolated on purpose.

### D1. Modular monolith, not microservices
One Spring Boot service with strict packages (`core`, `ai`, `persistence`, `api`, `config`). Microservices would
add network calls, deployment and tracing work with no benefit at this size. The `core` package has zero
framework imports, so it could be lifted into its own service later.

### D2. "The model extracts, the code decides"
The model does one thing: read the text and fill in fields (+ rate clarity). Everything that must be reliable is
plain Java: what is missing, which questions to ask, the status, the score, the final wording. Small open
models (Llama 8B) are good at extraction and bad at following complex rules, so this split plays to their
strengths, and it makes the core testable without any model.

### D3. Standardised text is rendered by code, not written by the model
The point of the project is a *fixed* format. A template renders it (`JustificationRenderer`), so every
justification has the same lines in the same order, and nothing the model "invents" can sneak into the wording.

### D4. Quality score = 60% completeness (code) + 40% clarity (model)
Most of the weight is deterministic so scores stay comparable over time in Power BI. Clarity still matters:
"source: the server" is complete but useless.

### D5. Ask at most 3 questions per round, at most 3 rounds
Employees abandon long forms. After 3 rounds the request goes through as `INCOMPLETE` (rendered with
"Not provided") rather than blocking someone's work. Both limits are in `application.yml`.

### D6. Ollama over HTTP directly, not Spring AI (for now)
We planned Spring AI. For the MVP the client calls Ollama's `/api/chat` with `RestClient` (≈60 lines) behind the
`AiClient` interface. Reasons: Spring AI's API has moved a lot between versions, the build sandbox could not
download it to verify a version, and we only need one call. Swapping in Spring AI (or any provider) = one new
`AiClient` class; nothing else changes.

### D7. Defensive JSON handling
`format: "json"` + temperature 0.1 + strict parsing + up to 2 retries. Placeholders such as "N/A", "unknown",
"غير محدد" are treated as missing. If the model still fails, the API answers 503 and nothing is stored.

### D8. Templates are YAML files
New request type = new YAML file, no code. Extra templates can be mounted from outside the jar
(`TEMPLATES_DIR`), and override bundled ones by id.

### D9. PostgreSQL + JSONB + Flyway
Extracted fields live in a JSONB column (templates differ, so no fixed columns). Flyway owns the schema
(`ddl-auto: none`); Hibernate never changes it.

### D10. A `reporting` schema of views for Power BI, with a read-only login
Power BI reads only `reporting.v_*`, never the tables, so the tables can evolve without breaking reports. The
`powerbi_reader` login can see those views and nothing else (verified: it gets "permission denied" on raw tables).
Dates in the views are in Asia/Riyadh time so "daily" means the working day.

### D11. Round history is stored (`submission_round`)
Needed to answer the most useful question for the business: *which information do people forget most?*
(`reporting.v_missing_fields`).

### D12. Frontend is fully isolated
Separate folder, separate build, separate container, no shared code. It only knows the REST API (`/api/v1`).
nginx serves it and proxies `/api` to the backend, so there is no CORS in production and an API key (if used)
is added by nginx and never shipped to the browser. Any other UI (Teams bot, ServiceNow form) uses the same API.

### D13. API key filter instead of Spring Security
The MVP only needs a shared secret for server-to-server callers. Real user authentication should come from the
company's SSO / API gateway in front of the service, not be re-invented here.

### D14. Safe retries for connectors
`source` + `externalRef` is unique: a connector that retries the same ticket gets the same submission back,
not a duplicate. Concurrent answers are guarded by optimistic locking (409 instead of lost updates).

### D15. Model call outside the DB transaction
The model can take tens of seconds; holding a DB connection that long would exhaust the pool under load.
Only the save step is transactional.

### D16. Versions
Java 21, Spring Boot 3.5.6, PostgreSQL 16, Testcontainers 1.21, React 19 + Vite 8 + TypeScript 5.9. Default model `llama3.1:8b`.

### D17. Out of scope for the MVP
Kafka, microservices, Spring Security/SSO, the Teams/ServiceNow connectors themselves, AWS deployment.
See `docs/BACKLOG.md`.

### D18. Groq as a second provider (`AI_PROVIDER=groq`)
`GroqAiClient` sits next to `OllamaAiClient` behind the same `AiClient` interface and reuses the prompt, parser
and retries (`ChatAiClient`). Groq is OpenAI-compatible and fast, which makes it handy for comparing a larger
Llama (70B) against the local 8B. **It is a cloud service**: use only dummy data with it until the security team
approves. Ollama stays the default so company data never leaves the machine. The key is read from
`GROQ_API_KEY` only; it is never committed or logged (`toString` masks it).

### D19. Answers are not sent back to the model
Round 2+ only merges the employee's answers into the fields (answers are trusted as-is, earlier values kept).
No model call means answering is instant, deterministic and cannot fail with 503. Clarity keeps its round-1
value. If answers ever need re-checking, re-extract in the service; the pipeline already supports it.

### D20. Code rebuilt from these documents
The first build's source code was lost; only the docs survived. The code was rebuilt from them, this time with
the full `mvn verify` running on GitHub Actions (39 unit tests + 6 integration tests on real PostgreSQL).
`backend/src/test/resources/docker-java.properties` pins Docker API 1.44 because current Docker engines refuse
the old default that Testcontainers sends (otherwise the integration test is silently skipped).

### D21. The prompt prefers null over a guess
The first evaluation showed that almost all errors were *invented* values (e.g. "urgent" read as a duration).
An invented value is worse than a missing one: nobody asks about it, so the engineer gets a wrong
justification. The prompt now says "when in doubt, null" and names the common traps. Numbers before/after are
in `docs/BACKLOG.md`.

### D22. Questions are localized, the justification is not
Employees read questions in their language (`label_ar` / `question_ar` in templates, `?lang=` or
`Accept-Language`). The standardised text stays English: it is the engineer's fixed format, and one format is
what makes requests comparable and reportable. Stored round questions are English; the API re-localizes from the
template on read, so switching language mid-request works.

### D23. Webhook: one URL, signed, after commit, best effort
A generic webhook is the cheapest connector: ServiceNow, Jira Automation and Power Automate can all receive a
POST. It fires only after the transaction commits (no events for rolled-back data), runs on a virtual thread so
a slow receiver never delays the employee, signs the body with HMAC-SHA256 (`X-Justify-Signature`, like GitHub)
and retries 5xx/timeouts with backoff (2s, 10s, 30s). No outbox table yet: a crash during delivery loses that
event, acceptable for notifications because the state is always readable via `GET /justifications/{id}`.

### D24. Engineer feedback can be changed, last one counts
`POST /justifications/{id}/feedback` upserts one verdict per submission, only once it is finished. It exists
to answer "does a high score really mean the engineer could act on it?" (`reporting.v_feedback`).

### D25. A free one-click demo on Render
`render.yaml` provisions PostgreSQL, the API and the static web app. The demo uses Groq because a free instance
cannot run a local model. The web app proxies `/api` through a Render rewrite, so the browser stays same-origin
as in Docker (D12).
