create sequence if not exists issue_activity_logs_activity_seq_seq;

alter table issue_activity_logs
    add column if not exists activity_seq bigint;

with ordered_activities as (
    select id, row_number() over (order by created_at, id) as row_number
    from issue_activity_logs
    where activity_seq is null
)
update issue_activity_logs logs
set activity_seq = ordered_activities.row_number
from ordered_activities
where logs.id = ordered_activities.id;

select setval(
    'issue_activity_logs_activity_seq_seq',
    greatest(coalesce((select max(activity_seq) from issue_activity_logs), 0), 1),
    true
);

alter table issue_activity_logs
    alter column activity_seq set default nextval('issue_activity_logs_activity_seq_seq'),
    alter column activity_seq set not null;

alter sequence issue_activity_logs_activity_seq_seq
    owned by issue_activity_logs.activity_seq;

create index idx_issue_activity_logs_issue_seq on issue_activity_logs (issue_id, activity_seq desc);
