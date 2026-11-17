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
Java 21, Spring Boot 3.5.6, PostgreSQL 16, React 19 + Vite 8 + TypeScript 5.9. Default model `llama3.1:8b`.

### D17. Out of scope for the MVP
Kafka, microservices, Spring Security/SSO, the Teams/ServiceNow connectors themselves, AWS deployment.
See `docs/BACKLOG.md`.
