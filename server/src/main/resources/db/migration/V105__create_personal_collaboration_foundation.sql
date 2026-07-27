create table project_personal_activity_read_states (
    workspace_id uuid not null,
    user_id uuid not null,
    read_through_sequence bigint not null default 0 check (read_through_sequence >= 0),
    updated_at timestamptz not null,
    primary key (workspace_id, user_id),
    constraint fk_project_personal_activity_read_user
        foreign key (workspace_id, user_id) references users(workspace_id, id)
        on delete cascade
);

create table project_reminder_preferences (
    workspace_id uuid not null,
    user_id uuid not null,
    timezone varchar(64) not null default 'UTC',
    approaching_minutes integer not null default 1440 check (
        approaching_minutes between 5 and 10080
    ),
    enabled boolean not null default true,
    updated_at timestamptz not null,
    primary key (workspace_id, user_id),
    constraint fk_project_reminder_preference_user
        foreign key (workspace_id, user_id) references users(workspace_id, id)
        on delete cascade
);

create table project_nudge_receipts (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    work_item_id uuid not null,
    sender_id uuid not null,
    recipient_id uuid not null,
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    status varchar(16) not null check (status = 'accepted'),
    created_at timestamptz not null,
    unique (workspace_id, sender_id, request_id),
    constraint fk_project_nudge_item
        foreign key (workspace_id, space_id, work_item_id)
        references project_work_items(workspace_id, space_id, id)
        on delete restrict,
    constraint fk_project_nudge_sender
        foreign key (workspace_id, sender_id) references users(workspace_id, id)
        on delete restrict,
    constraint fk_project_nudge_recipient
        foreign key (workspace_id, recipient_id) references users(workspace_id, id)
        on delete restrict,
    constraint ck_project_nudge_distinct_parties check (sender_id <> recipient_id)
);

create index idx_project_nudge_frequency
    on project_nudge_receipts (
        workspace_id, work_item_id, sender_id, recipient_id, created_at desc
    );

create function guard_project_nudge_receipt()
returns trigger
language plpgsql
as $$
begin
    raise exception 'nudge receipts are immutable' using errcode = '23514';
end;
$$;

create trigger trg_project_nudge_receipt
before update or delete on project_nudge_receipts
for each row execute function guard_project_nudge_receipt();

alter table notifications
    add column invalidated_at timestamptz;

create index idx_notifications_personal_visible
    on notifications (workspace_id, recipient_id, created_at desc)
    where invalidated_at is null;
