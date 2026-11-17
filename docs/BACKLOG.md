# Backlog (ready to load into Jira)

Status: ✅ done in the MVP · ⬜ to do. Each epic below maps to a Jira Epic, each line to a Story.

## EPIC 1: Core evaluation API ✅
- ✅ Submit justification, get NEEDS_INFO + questions or COMPLETE + standardised text
- ✅ Answer questions across rounds (max 3 rounds, max 3 questions per round)
- ✅ Fixed-format rendering, quality score
- ✅ Idempotent submit per source system, optimistic locking
- ✅ Run `mvn verify` in CI: 39 unit + 6 integration tests green
- ⬜ Start the full `docker compose up` stack on a real machine and click through the web app

## EPIC 2: AI model ✅ (first version)
- ✅ Ollama client for Llama with JSON mode, strict parsing, retries
- ✅ Golden dataset + evaluation test
- ✅ Groq provider (`AI_PROVIDER=groq`) for larger hosted Llama models
- ✅ Prompt-evaluation workflow on GitHub Actions
- ⬜ Add the `GROQ_API_KEY` repository secret so the Groq models are measured too
- ✅ Run the evaluation, record baseline numbers (below)
- ⬜ Decide the default model: qwen2.5:7b is more accurate, llama3.1:8b slightly faster to read clarity
- ⬜ Remaining error type: the model infers an impact from a purpose ("so we get alerts" → impact). Add golden cases for it
- ⬜ Grow the golden set (10 cases today) to 50 cases from real (anonymised) justifications
- ⬜ Compare 2-3 models (llama3.1:8b, qwen2.5:7b, a larger Llama) on accuracy vs latency

## EPIC 3: Templates
- ✅ Firewall rule template
- ✅ External templates folder, override by id
- ⬜ Access request template
- ⬜ Change request template
- ⬜ `GET /templates` shows per-language labels (Arabic/English)

## EPIC 4: Data & Power BI ✅ (first version)
- ✅ Submissions and rounds stored, reporting views, read-only login
- ⬜ Build the first dashboard (see docs/POWERBI.md)
- ⬜ Engineer feedback: "was this justification enough?" (yes/no) stored per submission, to validate the score

## EPIC 5: Connectors
- ⬜ Generic outbound webhook: notify a URL when a submission becomes COMPLETE
- ⬜ ServiceNow: business rule on request submit → call API with `source=servicenow`, `externalRef=RITM…`; write the standardised text back to the ticket
- ⬜ Jira: Automation rule on issue created → call API; comment with questions / standardised text
- ⬜ Teams bot: conversation that asks the questions in chat
- ⬜ Embeddable JS widget for any web form

## EPIC 6: Security & operations
- ⬜ Put behind company SSO / API gateway (replace shared API key for users)
- ⬜ Mask or avoid storing sensitive values (internal IPs) if the security team requires it
- ⬜ Structured logs + request id, health/readiness probes for Kubernetes
- ⬜ Deploy to AWS (ECS or EKS) with RDS PostgreSQL; model on a GPU instance or an approved hosted model

## EPIC 7: Frontend
- ✅ Isolated React app: submit, answer, copy result, Arabic/English
- ⬜ History page ("my requests")
- ⬜ Component tests (Vitest + Testing Library)

## Prompt evaluation results

Golden set: 10 cases (English, Arabic, mixed). Accuracy = per required field, did the model correctly say
present / missing. Run on the GitHub Actions CPU runner (Ollama), so the seconds are slow on purpose.

| Date | Prompt | Model | Unusable | Field accuracy | Clarity clear / vague | Seconds (10 cases) |
|---|---|---|---|---|---|---|
| 2026-11-17 | v1 | llama3.1:8b | 0 | 86.7% | 8.0 / 4.0 | 134 |
| 2026-11-17 | v1 | qwen2.5:7b | 0 | 90.0% | 8.6 / 2.6 | 185 |
| 2026-11-17 | v2 (D21: null over guess) | llama3.1:8b | 0 | **95.0%** | 9.0 / 3.2 | 277 |
| 2026-11-17 | v2 (D21: null over guess) | qwen2.5:7b | 0 | **98.3%** | 8.6 / 3.4 | 255 |

Groq models (llama-3.3-70b-versatile, llama-3.1-8b-instant): not measured yet, add the `GROQ_API_KEY` secret.
