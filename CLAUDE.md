# Justify AI: notes for coding agents

Nawaf's project (backend engineer, Java/Spring). It is a CV/portfolio piece, so code quality, tests and
clear docs matter more than features. Talk to him in Saudi Najdi dialect, keep it light. He has given full
autonomy: decide, fix and push without asking, unless something is irreversible and genuinely ambiguous.

## Where things are

- Spec and rationale: `README.md`, `DECISIONS.md` (D1-D29), `docs/ARCHITECTURE.md`, `docs/TESTING.md`,
  `docs/POWERBI.md`, `docs/BACKLOG.md` (what is left, prompt-eval results).
- Backend: `backend/` (Java 21, Spring Boot 3.5, Maven). Packages `core` (no framework imports),
  `core.pipeline`, `ai`, `template`, `persistence`, `service`, `notify`, `firewall` (request portal), `api`, `config`.
- Frontend: `frontend/` (React 19, Vite, TypeScript, Vitest). Talks to `/api/v1` only.

## Commands

```bash
cd backend && mvn verify                 # 54 unit + 10 integration tests (needs Docker for Testcontainers)
cd frontend && npm test && npm run build
RUN_AI_EVAL=true mvn verify -Dit.test=PromptEvalIT   # real model, see docs/TESTING.md
```

CI (`.github/workflows/ci.yml`) runs both on every push and fails if the integration test was skipped.
`prompt-eval.yml` measures the prompt on real models when `PromptBuilder` or the golden set changes.

## Rules

- The model extracts, the code decides (D2); the standardised text is rendered by code (D3), always English (D22).
- Change the prompt → run the prompt evaluation and record the numbers in `docs/BACKLOG.md`.
- Schema changes only through a new Flyway migration; Power BI reads only `reporting.v_*` views.
- Never commit secrets (`.env` is git-ignored). Real company data never goes to Groq.
- Record any new decision in `DECISIONS.md`.
