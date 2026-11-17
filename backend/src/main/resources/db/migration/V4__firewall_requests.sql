-- Firewall request portal: the structured request, linked to its AI-checked justification.
create sequence firewall_ticket_seq;

create table firewall_request (
    id                uuid primary key,
    ticket_no         varchar(20)  not null unique,
    requester_name    varchar(120) not null,
    employee_id       varchar(40)  not null,
    department        varchar(120) not null,
    email             varchar(200),
    source_hostname   varchar(255) not null,
    source_ip         varchar(64)  not null,
    destination_host  varchar(255) not null,
    destination_ip    varchar(64),
    protocol          varchar(8)   not null check (protocol in ('TCP', 'UDP')),
    ports             varchar(100) not null,
    duration_type     varchar(12)  not null check (duration_type in ('PERMANENT', 'TEMPORARY')),
    end_date          date,
    submission_id     uuid         not null unique references submission (id),
    status            varchar(16)  not null
                      check (status in ('AWAITING_INFO', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    decided_by        varchar(120),
    decision_note     varchar(1000),
    decided_at        timestamptz,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    version           bigint       not null default 0,
    constraint ck_end_date check (duration_type = 'PERMANENT' or end_date is not null)
);

create index ix_firewall_request_status on firewall_request (status, created_at desc);
create index ix_firewall_request_employee on firewall_request (employee_id);

create view reporting.v_firewall_requests as
select f.id,
       f.ticket_no,
       f.department,
       f.protocol,
       f.ports,
       f.duration_type,
       f.status,
       s.status        as justification_status,
       s.quality_score,
       s.round_count,
       (s.status = 'COMPLETE' and s.round_count = 1) as first_pass_complete,
       f.decided_at,
       case when f.decided_at is not null
            then round(extract(epoch from (f.decided_at - f.created_at)) / 3600.0, 1) end as hours_to_decision,
       (f.created_at at time zone 'Asia/Riyadh')::date as created_date
from public.firewall_request f
join public.submission s on s.id = f.submission_id;
