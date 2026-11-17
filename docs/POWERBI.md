# Power BI

Every submission is stored in PostgreSQL the moment it is evaluated, and every round is kept. Power BI reads
a dedicated `reporting` schema through a read-only login.

## Views

| View | One row per | Use it for |
|---|---|---|
| `reporting.v_submissions` | submission | Volumes, status, quality score, rounds, time to complete, first-pass rate |
| `reporting.v_missing_fields` | submission × round × missing field | Which information employees forget most, per template and per source system |
| `reporting.v_daily_summary` | day × template | Ready-made daily trend (totals, avg quality, avg rounds, first-pass %) |
| `reporting.v_feedback` | submission with engineer feedback | Does the quality score match the engineer's verdict (`sufficient`)? |

Key columns: `is_complete`, `first_pass_complete` (complete with no questions asked: the headline KPI),
`round_count`, `quality_score` (0-10), `minutes_to_complete`, `created_date` (Asia/Riyadh).

## Connect

1. Set `POWERBI_READER_PASSWORD` in `.env` **before the first start** (the login is created when the database is
   initialised). On an existing database, create it by hand:
   ```sql
   create role powerbi_reader login password '...';
   grant usage on schema reporting to powerbi_reader;
   grant select on all tables in schema reporting to powerbi_reader;
   ```
2. Power BI Desktop → **Get data** → **PostgreSQL database**. Server `<host>:5432`, database `justify`.
   (Depending on the Power BI version, the Npgsql provider may need to be installed.)
3. Log in as `powerbi_reader`, pick the `reporting` views.
4. **Import** mode is enough for daily reporting; use DirectQuery only if you need live numbers.
5. Publishing to the Power BI Service with a database inside the company network needs an
   **on-premises data gateway** (your BI/IT team usually already runs one).

## Suggested measures (DAX)

```DAX
Total Requests     = COUNTROWS ( v_submissions )
First Pass Rate %  = DIVIDE ( CALCULATE ( [Total Requests], v_submissions[first_pass_complete] = TRUE () ), [Total Requests] ) * 100
Avg Quality        = AVERAGE ( v_submissions[quality_score] )
Avg Rounds         = AVERAGE ( v_submissions[round_count] )
Incomplete %       = DIVIDE ( CALCULATE ( [Total Requests], v_submissions[status] = "INCOMPLETE" ), [Total Requests] ) * 100
Sufficient %       = DIVIDE ( CALCULATE ( COUNTROWS ( v_feedback ), v_feedback[sufficient] = TRUE () ), COUNTROWS ( v_feedback ) ) * 100
```

## Suggested first dashboard

- KPI cards: Total Requests, First Pass Rate %, Avg Quality, Avg Rounds
- Line: First Pass Rate % by `created_date` (is quality improving?)
- Bar: count of `v_missing_fields[field_key]` (what to train employees on, or what to add to the request form)
- Matrix: `source` × Avg Quality (which entry channel produces the weakest requests)
- Scatter / bins: `v_feedback[quality_score]` vs Sufficient % (is a score of 8 really "good enough"?)

## Adding a report

Never point Power BI at the raw tables. Add a view in a new Flyway migration
(`backend/src/main/resources/db/migration/V4__....sql`) under the `reporting` schema; the read-only login gets
access automatically (default privileges).
