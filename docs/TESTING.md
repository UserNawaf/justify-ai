# Testing

AI output is not deterministic, so the strategy splits tests into what must always pass and what measures
the model.

## 1. Unit tests: fast, no model, no database (`*Test`, run by `mvn test`)

| Test | What it proves |
|---|---|
| `EvaluationPipelineTest` | Status decisions, question cap and order, confirmed answers win, placeholders = missing, last round → INCOMPLETE |
| `QualityScorerTest` | Score formula and clamping |
| `JustificationRendererTest` | Fixed format, field order, "Not provided" |
| `ExtractionParserTest` | Accepts code fences/chatter, rejects malformed replies, drops invented keys |
| `PromptBuilderTest` | Prompt lists every field and its requirement |
| `YamlTemplateParserTest` | Template validation; refuses arbitrary YAML types; every bundled template fully translated |
| `ApiKeyFilterTest` | Key check, CORS preflight and non-API paths pass |
| `JustificationControllerTest` | HTTP contract: status codes, validation, language selection, no internal detail leaked on 503 |
| `LanguageTest` | `Accept-Language` parsing, Arabic with English fallback |
| `WebhookNotifierTest` | Payload, HMAC signature (checked against openssl), retries on 5xx, gives up quietly (real local HTTP server) |

The model is replaced by `FakeAiClient`, which reads `"field: value"` lines, so results are exact and repeatable.

## 2. Integration test: real PostgreSQL (`JustificationFlowIT`, run by `mvn verify`)

Starts PostgreSQL in Docker (Testcontainers), runs the Flyway migrations, and walks the whole flow through
HTTP: vague text → questions → answers → COMPLETE → duplicate retry returns the same id → 409 on a closed
submission → signed webhook received → engineer feedback → the row, its missing fields and the feedback appear
in the Power BI views; Arabic questions on the access-request template; model outage stores nothing; the
`powerbi_reader` login reads the views and is refused on the tables. Skipped automatically if Docker is
not available (check the CI log says `Skipped: 0` for it).

## 3. Prompt evaluation: real model (`PromptEvalIT`, manual)

`src/test/resources/eval/golden-firewall.json` holds hand-labelled justifications (English and Arabic, vague to
complete). The test runs each through the real model and reports:

- **unusable replies**: never valid JSON after retries (must be 0)
- **field accuracy**: for each required field, did the model correctly say present / missing (default ≥ 75%)
- **clarity separation**: clear texts must score higher than vague ones

```bash
ollama pull llama3.1:8b
cd backend
RUN_AI_EVAL=true mvn verify -Dit.test=PromptEvalIT
# other model:  RUN_AI_EVAL=true OLLAMA_MODEL=qwen2.5:7b mvn verify -Dit.test=PromptEvalIT
```

On GitHub, `.github/workflows/prompt-eval.yml` runs it automatically (Ollama `llama3.1:8b` and `qwen2.5:7b`
on the runner's CPU, plus Groq models if the `GROQ_API_KEY` secret exists) whenever `PromptBuilder`, the
golden set or the workflow change, and on demand from the Actions tab. The report is in the job summary.
With Groq locally: `RUN_AI_EVAL=true AI_PROVIDER=groq GROQ_API_KEY=... mvn verify -Dit.test=PromptEvalIT`.

Run it every time you change the prompt (`PromptBuilder`) or the model, and compare the numbers. Add a case
to the golden file whenever a real justification is handled badly: the set should grow with experience.

## 4. CI

`.github/workflows/ci.yml` runs unit + integration tests and the frontend build on every push.
The prompt evaluation is not in CI (it needs a model and its results vary).

## Frontend

`npm test` runs Vitest + Testing Library against a mocked `fetch`: the submit → questions → answer → result flow
in Arabic (RTL), the friendly 503 message, and switching to English. `npm run build` type-checks and builds.
Both run in CI.
