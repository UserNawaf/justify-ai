create table submission (
    id                 uuid primary key,
    template_id        varchar(64)  not null,
    source             varchar(64)  not null default 'web',
    external_ref       varchar(128),
    status             varchar(16)  not null check (status in ('NEEDS_INFO', 'COMPLETE', 'INCOMPLETE')),
    round_count        integer      not null default 1,
    quality_score      numeric(3,1) not null,
    completeness       numeric(4,3) not null,
    clarity            integer      not null,
    standardized_text  text,
    extracted_fields   jsonb        not null default '{}'::jsonb,
    raw_text           text         not null,
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null,
    completed_at       timestamptz,
    version            bigint       not null default 0,
    constraint uq_submission_source_ref unique (source, external_ref)
);

create index ix_submission_created_at on submission (created_at);
create index ix_submission_template on submission (template_id);

create table submission_round (
    id             bigserial primary key,
    submission_id  uuid        not null references submission (id) on delete cascade,
    round_no       integer     not null,
    input_text     text        not null,
    missing_fields jsonb       not null default '[]'::jsonb,
    questions      jsonb       not null default '{}'::jsonb,
    clarity        integer     not null,
    created_at     timestamptz not null,
    constraint uq_round unique (submission_id, round_no)
);
