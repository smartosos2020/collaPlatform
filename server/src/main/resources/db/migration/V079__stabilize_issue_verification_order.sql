create sequence issue_verification_logs_sequence_no_seq;

alter table issue_verification_logs
    add column sequence_no bigint;

with ordered as (
    select id, row_number() over (order by created_at, id) as sequence_no
      from issue_verification_logs
)
update issue_verification_logs target
   set sequence_no = ordered.sequence_no
  from ordered
 where target.id = ordered.id;

select setval(
    'issue_verification_logs_sequence_no_seq',
    coalesce((select max(sequence_no) + 1 from issue_verification_logs), 1),
    false
);

alter table issue_verification_logs
    alter column sequence_no set default nextval('issue_verification_logs_sequence_no_seq'),
    alter column sequence_no set not null;

alter sequence issue_verification_logs_sequence_no_seq
    owned by issue_verification_logs.sequence_no;

create index idx_issue_verification_logs_issue_sequence
    on issue_verification_logs (issue_id, sequence_no desc);
