-- Power BI reads only these views, never the tables (D10). Dates are Asia/Riyadh.
create schema if not exists reporting;

create view reporting.v_submissions as
select s.id,
       s.template_id,
       s.source,
       s.external_ref,
       s.status,
       s.status = 'COMPLETE'                                          as is_complete,
       (s.status = 'COMPLETE' and s.round_count = 1)                  as first_pass_complete,
       s.round_count,
       s.quality_score,
       s.completeness,
       s.clarity,
       s.created_at,
       s.completed_at,
       case when s.completed_at is not null
            then round(extract(epoch from (s.completed_at - s.created_at)) / 60.0, 1) end as minutes_to_complete,
       (s.created_at at time zone 'Asia/Riyadh')::date                as created_date
from public.submission s;

create view reporting.v_missing_fields as
select r.submission_id,
       s.template_id,
       s.source,
       r.round_no,
       f.field_key,
       (r.created_at at time zone 'Asia/Riyadh')::date as created_date
from public.submission_round r
join public.submission s on s.id = r.submission_id
cross join lateral jsonb_array_elements_text(r.missing_fields) as f(field_key);

create view reporting.v_daily_summary as
select created_date,
       template_id,
       count(*)                                                        as total_requests,
       count(*) filter (where is_complete)                            as completed,
       count(*) filter (where status = 'INCOMPLETE')                  as incomplete,
       round(avg(quality_score), 2)                                   as avg_quality,
       round(avg(round_count), 2)                                     as avg_rounds,
       round(100.0 * count(*) filter (where first_pass_complete) / count(*), 1) as first_pass_pct
from reporting.v_submissions
group by created_date, template_id;

-- The read-only login is created by db/init on first start. Grant only if it exists, so migrations
-- also run on databases (tests, existing installs) where it was not created.
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'powerbi_reader') then
        execute 'grant usage on schema reporting to powerbi_reader';
        execute 'grant select on all tables in schema reporting to powerbi_reader';
        execute 'alter default privileges in schema reporting grant select on tables to powerbi_reader';
    end if;
end
$$;
