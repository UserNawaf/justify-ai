-- Enterprise request form: request type, priority, application, environment, start date, notes.
-- Network fields are IP-first; hostnames become optional.
alter table firewall_request
    add column request_type     varchar(16)  not null default 'NEW_ACCESS'
        check (request_type in ('NEW_ACCESS', 'MODIFY_ACCESS', 'REMOVE_ACCESS')),
    add column priority         varchar(8)   not null default 'MEDIUM'
        check (priority in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    add column application_name varchar(200) not null default 'Unspecified',
    add column environment      varchar(12)  not null default 'PRODUCTION'
        check (environment in ('PRODUCTION', 'UAT', 'DEVELOPMENT', 'DR')),
    add column start_date       date,
    add column notes            varchar(2000),
    alter column source_hostname drop not null,
    alter column destination_host drop not null;

update firewall_request set start_date = (created_at at time zone 'Asia/Riyadh')::date where start_date is null;
alter table firewall_request alter column start_date set not null;

-- Existing columns keep their order; new ones are appended (allowed by create or replace view).
create or replace view reporting.v_firewall_requests as
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
       (f.created_at at time zone 'Asia/Riyadh')::date as created_date,
       f.request_type,
       f.priority,
       f.application_name,
       f.environment
from public.firewall_request f
join public.submission s on s.id = f.submission_id;
