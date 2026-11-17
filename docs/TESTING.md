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
| `YamlTemplateParserTest` | Template validation; refuses arbitrary YAML types |
| `ApiKeyFilterTest` | Key check, CORS preflight and non-API paths pass |
| `JustificationControllerTest` | HTTP contract: status codes, validation, no internal detail leaked on 503 |

The model is replaced by `FakeAiClient`, which reads `"field: value"` lines, so results are exact and repeatable.

## 2. Integration test: real PostgreSQL (`JustificationFlowIT`, run by `mvn verify`)

Starts PostgreSQL in Docker (Testcontainers), runs the Flyway migrations, and walks the whole flow through
HTTP: vague text → questions → answers → COMPLETE → duplicate retry returns the same id → 409 on a closed
submission → the row and its missing fields appear in the Power BI views. Skipped automatically if Docker is
not available.

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

Run it every time you change the prompt (`PromptBuilder`) or the model, and compare the numbers. Add a case
to the golden file whenever a real justification is handled badly: the set should grow with experience.

## 4. CI

`.github/workflows/ci.yml` runs unit + integration tests and the frontend build on every push.
The prompt evaluation is not in CI (it needs a model and its results vary).

## Frontend

`npm run build` type-checks (`tsc`) and builds. No UI tests yet; the UI is thin and all logic is in the API.
