create table project_cross_team_panorama_preferences (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    user_id uuid not null,
    compact boolean not null default false,
    window_days smallint not null default 30 check (window_days between 1 and 90),
    version bigint not null default 1 check (version > 0),
    updated_at timestamptz not null default now(),
    constraint uk_project_cross_team_panorama_preference unique (workspace_id,space_id,user_id),
    constraint fk_project_cross_team_panorama_preference_space
      foreign key (workspace_id,space_id) references project_spaces(workspace_id,id) on delete cascade,
    constraint fk_project_cross_team_panorama_preference_user
      foreign key (workspace_id,user_id) references users(workspace_id,id)
);

create table project_cross_team_panorama_slice_stats (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    slice_kind varchar(24) not null check (slice_kind in ('grant','relation','sync','conflict')),
    status_key varchar(24) not null,
    fact_count integer not null check (fact_count between 0 and 10000),
    source_fingerprint varchar(64) not null check (source_fingerprint ~ '^[0-9a-f]{64}$'),
    expires_at timestamptz not null,
    rebuilt_at timestamptz not null default now(),
    constraint uk_project_cross_team_panorama_stat
      unique (workspace_id,space_id,slice_kind,status_key),
    constraint fk_project_cross_team_panorama_stat_space
      foreign key (workspace_id,space_id) references project_spaces(workspace_id,id) on delete cascade
);
create index idx_project_cross_team_panorama_stat_expiry
  on project_cross_team_panorama_slice_stats(workspace_id,space_id,expires_at);

create table project_cross_team_panorama_governance_receipts (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    operation varchar(32) not null check (operation in ('preference_save','panorama_rebuild')),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    response_payload jsonb not null check (jsonb_typeof(response_payload)='object'),
    created_at timestamptz not null default now(),
    constraint uk_project_cross_team_panorama_receipt
      unique (workspace_id,actor_id,operation,request_id),
    constraint fk_project_cross_team_panorama_receipt_space
      foreign key (workspace_id,space_id) references project_spaces(workspace_id,id) on delete cascade,
    constraint fk_project_cross_team_panorama_receipt_actor
      foreign key (workspace_id,actor_id) references users(workspace_id,id)
);

create trigger trg_project_cross_team_panorama_receipt_immutable
before update or delete on project_cross_team_panorama_governance_receipts
for each row execute function guard_project_cross_space_sync_immutable();
