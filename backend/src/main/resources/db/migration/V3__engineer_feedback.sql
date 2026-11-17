-- Engineer's verdict on a finished justification: was it enough to act on? Validates the quality score.
create table submission_feedback (
    submission_id uuid primary key references submission (id) on delete cascade,
    sufficient    boolean     not null,
    comment       varchar(1000),
    created_at    timestamptz not null,
    updated_at    timestamptz not null
);

-- Granted to powerbi_reader automatically (default privileges from V2).
create view reporting.v_feedback as
select f.submission_id,
       s.template_id,
       s.source,
       s.status,
       s.quality_score,
       s.round_count,
       f.sufficient,
       f.comment,
       (f.created_at at time zone 'Asia/Riyadh')::date as feedback_date
from public.submission_feedback f
join public.submission s on s.id = f.submission_id;
