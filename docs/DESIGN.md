# Design: use cases and diagrams

All diagrams are Mermaid, so GitHub renders them directly.

## 1. Actors

| Actor | Who | Goal |
|---|---|---|
| Requester | Employee who needs network access | Submit a firewall request without being bounced back for details |
| Network engineer | Reviews and decides requests | Receive complete, standardised requests and approve or reject quickly |
| External system | Any ticketing or portal system calling the API | Use the justification checker as a service (`source` + `externalRef`) |
| Analyst | Reads Power BI | Measure quality and turnaround through `reporting.v_*` views |
| AI model | Ollama (local) or Groq (cloud, dummy data only) | Extract fields from free text; score clarity |

## 2. Use cases

```mermaid
flowchart LR
    R([Requester])
    E([Network engineer])
    X([External system])
    A([Analyst])

    subgraph Justify AI
        UC1(UC1 Submit firewall request)
        UC2(UC2 Answer follow-up questions)
        UC3(UC3 Track request status)
        UC4(UC4 Check a justification only)
        UC5(UC5 Review queue and detail)
        UC6(UC6 Approve or reject)
        UC7(UC7 Give feedback on justification)
        UC8(UC8 Receive signed webhook)
        UC9(UC9 View reporting)
    end

    R --> UC1
    R --> UC2
    R --> UC3
    R --> UC4
    E --> UC5
    E --> UC6
    E --> UC7
    X --> UC4
    X --> UC8
    A --> UC9
    UC1 -. include .-> UC2
    UC6 -. extend .-> UC7
```

| ID | Use case | Main flow | Alternative / error flows |
|---|---|---|---|
| UC1 | Submit firewall request | Requester fills the form; code validates technical fields; the justification is checked; a ticket `FW-YYYY-NNNNN` is created | Invalid IP/CIDR, `/0`, bad ports, temporary request over 365 days: field-level errors (RFC 9457) |
| UC2 | Answer follow-up questions | System asks at most 3 questions per round, up to 3 rounds; answers are trusted without a model call | After round 3 the request goes to review as INCOMPLETE |
| UC3 | Track request status | Requester opens the ticket and sees AWAITING_INFO, PENDING_REVIEW, APPROVED or REJECTED | Unknown ticket: 404 problem+json |
| UC4 | Check a justification only | Free text goes in; structured fields, score and standardised English text come out | Retry with the same `source` + `externalRef` returns the same result (idempotent) |
| UC5 | Review queue and detail | Engineer sees the queue with score and standardised text next to the raw text | None |
| UC6 | Approve or reject | Engineer decides with a note; status changes; completion listener fires | Concurrent decision: optimistic lock conflict (409) |
| UC7 | Give feedback | Engineer marks the justification sufficient or not | Feeds `reporting.v_feedback` to validate the score |
| UC8 | Receive signed webhook | External system gets a POST with an `X-Justify-Signature` HMAC | Failure: retries after 2s, 10s, 30s |
| UC9 | View reporting | Power BI reads `reporting.v_*` through the read-only `powerbi_reader` role | None |

## 3. Architecture

```mermaid
flowchart TB
    subgraph Client
        UI[React 19 + Vite + TS<br/>AR/EN, RTL]
    end
    subgraph Backend[Spring Boot 3.5, Java 21]
        API[api: controllers, problem+json]
        FW[firewall: form, validator, status]
        SVC[service: JustificationService]
        CORE[core + core.pipeline<br/>no framework imports]
        TPL[template: YAML templates]
        AI[ai: PromptBuilder, parser, clients]
        NOTIFY[notify: HMAC webhook]
    end
    DB[(PostgreSQL 16<br/>JSONB, Flyway)]
    OLL[Ollama<br/>Llama / Qwen]
    GRQ[Groq cloud<br/>dummy data only]
    PBI[Power BI]
    EXT[External system]

    UI -->|/api/v1| API
    API --> FW --> SVC
    API --> SVC
    SVC --> CORE
    CORE --> TPL
    SVC --> AI
    AI --> OLL
    AI -.-> GRQ
    SVC --> DB
    FW --> DB
    SVC --> NOTIFY --> EXT
    PBI -->|reporting.v_*| DB
```

## 4. Sequence: submit a request with a vague justification

```mermaid
sequenceDiagram
    actor R as Requester
    participant UI as React UI
    participant API as API
    participant V as Form validator
    participant P as Pipeline (code)
    participant M as Model
    participant DB as PostgreSQL

    R->>UI: Fill form + justification
    UI->>API: POST /firewall-requests
    API->>V: Validate IPs, ports, dates
    V-->>API: OK
    API->>P: evaluateText(template, text, knownFields)
    P->>M: Extract fields (JSON only)
    M-->>P: fields, clarity
    P->>P: merge, decide, score 60/40
    P-->>API: NEEDS_INFO + 1 question
    API->>DB: save submission + request (AWAITING_INFO)
    API-->>UI: ticket FW-2026-00001 + question
    R->>UI: Answer
    UI->>API: POST /answers
    API->>P: applyAnswers (no model call)
    P-->>API: COMPLETE + standardised text
    API->>DB: update, status PENDING_REVIEW
    API-->>UI: Done
```

The model call happens outside the database transaction (D15), so a slow model never holds a connection.

## 5. Request status

```mermaid
stateDiagram-v2
    [*] --> AWAITING_INFO: submitted, questions pending
    [*] --> PENDING_REVIEW: justification complete
    AWAITING_INFO --> PENDING_REVIEW: answers complete or rounds exhausted
    PENDING_REVIEW --> APPROVED: engineer approves
    PENDING_REVIEW --> REJECTED: engineer rejects
    APPROVED --> [*]
    REJECTED --> [*]
```

## 6. Data model

```mermaid
erDiagram
    submission ||--o{ submission_round : has
    submission ||--o| submission_feedback : receives
    submission ||--o| firewall_request : backs

    submission {
        uuid id PK
        varchar template_id
        varchar source
        varchar external_ref
        varchar status
        int round_count
        numeric quality_score
        text standardized_text
        jsonb extracted_fields
        text raw_text
        bigint version
    }
    submission_round {
        bigint id PK
        uuid submission_id FK
        int round_no
        jsonb missing_fields
        jsonb questions
    }
    submission_feedback {
        uuid submission_id PK
        boolean sufficient
        varchar comment
    }
    firewall_request {
        uuid id PK
        varchar ticket_no
        varchar requester_name
        varchar source_ip
        varchar destination_host
        varchar protocol
        varchar ports
        varchar duration_type
        uuid submission_id FK
        varchar status
        varchar decided_by
        bigint version
    }
```

`UNIQUE (source, external_ref)` on `submission` makes retries idempotent (D14).

## 7. Scoring

```mermaid
flowchart LR
    T[Free text] --> M[Model extracts fields + clarity 1-5]
    M --> C[Code: completeness = filled required fields / required]
    M --> K[Model: clarity]
    C --> S[Score = 60% completeness + 40% clarity]
    K --> S
    S --> D{All required fields?}
    D -- no, rounds left --> Q[Ask max 3 questions]
    D -- yes or rounds exhausted --> R[Code renders English text]
```
