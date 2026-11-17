# Backlog (ready to load into Jira)

Status: ✅ done in the MVP · ⬜ to do. Each epic below maps to a Jira Epic, each line to a Story.

## EPIC 1: Core evaluation API ✅
- ✅ Submit justification, get NEEDS_INFO + questions or COMPLETE + standardised text
- ✅ Answer questions across rounds (max 3 rounds, max 3 questions per round)
- ✅ Fixed-format rendering, quality score
- ✅ Idempotent submit per source system, optimistic locking
- ⬜ Run `mvn verify` locally / in CI and fix anything the sandbox could not compile (see README "Status")

## EPIC 2: AI model ✅ (first version)
- ✅ Ollama client for Llama with JSON mode, strict parsing, retries
- ✅ Golden dataset + evaluation test
- ⬜ Run the evaluation on llama3.1:8b, record baseline numbers in this file
- ⬜ Grow the golden set to 50 cases from real (anonymised) justifications
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
