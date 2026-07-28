create table project_metric_data_source_bindings (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    dashboard_id uuid not null,
    binding_key varchar(64) not null check (binding_key ~ '^[a-z][a-z0-9_.-]{1,63}$'),
    source_kind varchar(32) not null check (
      source_kind in ('work_item_query','saved_view','cross_space_panorama')
    ),
    source_space_ids jsonb not null check (jsonb_typeof(source_space_ids) = 'array'),
    saved_view_id uuid,
    metric_id uuid not null,
    metric_version integer not null check (metric_version >= 1),
    row_version bigint not null default 1 check (row_version >= 1),
    created_at timestamptz not null default now(),
    constraint uk_project_metric_source_binding unique (
      workspace_id,space_id,dashboard_id,binding_key
    ),
    constraint uk_project_metric_source_binding_boundary unique (
      workspace_id,space_id,id
    ),
    constraint fk_project_metric_source_binding_space foreign key (workspace_id,space_id)
      references project_spaces(workspace_id,id),
    constraint fk_project_metric_source_binding_metric foreign key (workspace_id,space_id,metric_id)
      references project_metric_definitions(workspace_id,space_id,id)
);
create index idx_project_metric_source_binding_dashboard
  on project_metric_data_source_bindings(workspace_id,space_id,dashboard_id,binding_key);

create table project_dashboards (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    dashboard_key varchar(64) not null check (dashboard_key ~ '^[a-z][a-z0-9_.-]{1,63}$'),
    name varchar(160) not null,
    description varchar(2000) not null default '',
    status varchar(16) not null check (status in ('draft','active','disabled','archived')),
    sharing_scope varchar(16) not null default 'private' check (sharing_scope in ('private','space')),
    row_version bigint not null check (row_version >= 1),
    draft_config jsonb not null check (jsonb_typeof(draft_config) = 'object'),
    current_version_id uuid,
    created_by uuid not null,
    updated_by uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_project_dashboard_boundary unique (workspace_id,space_id,id),
    constraint uk_project_dashboard_key unique (workspace_id,space_id,dashboard_key),
    constraint fk_project_dashboard_space foreign key (workspace_id,space_id)
      references project_spaces(workspace_id,id),
    constraint fk_project_dashboard_creator foreign key (workspace_id,created_by)
      references users(workspace_id,id),
    constraint fk_project_dashboard_updater foreign key (workspace_id,updated_by)
      references users(workspace_id,id)
);
create index idx_project_dashboard_list
  on project_dashboards(workspace_id,space_id,status,updated_at desc,id);

alter table project_metric_data_source_bindings
  add constraint fk_project_metric_source_binding_dashboard
  foreign key (workspace_id,space_id,dashboard_id)
  references project_dashboards(workspace_id,space_id,id);

create table project_chart_definitions (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    dashboard_id uuid not null,
    chart_key varchar(64) not null check (chart_key ~ '^[a-z][a-z0-9_.-]{1,63}$'),
    name varchar(160) not null,
    visualization varchar(24) not null check (
      visualization in ('table','metric_card','line','bar','stacked_bar','distribution')
    ),
    draft_config jsonb not null check (jsonb_typeof(draft_config) = 'object'),
    status varchar(16) not null check (status in ('draft','active','archived')),
    row_version bigint not null check (row_version >= 1),
    current_version_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_project_chart_definition_boundary unique (workspace_id,space_id,id),
    constraint uk_project_chart_definition_key unique (
      workspace_id,space_id,dashboard_id,chart_key
    ),
    constraint fk_project_chart_definition_dashboard foreign key (
      workspace_id,space_id,dashboard_id
    ) references project_dashboards(workspace_id,space_id,id)
);
create index idx_project_chart_definition_dashboard
  on project_chart_definitions(workspace_id,space_id,dashboard_id,status,chart_key);

create table project_chart_versions (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    chart_id uuid not null,
    version_number integer not null check (version_number >= 1),
    schema_version integer not null check (schema_version = 1),
    definition_hash varchar(64) not null check (definition_hash ~ '^[0-9a-f]{64}$'),
    config jsonb not null check (jsonb_typeof(config) = 'object'),
    published_by uuid not null,
    published_at timestamptz not null default now(),
    constraint uk_project_chart_version_boundary unique (workspace_id,space_id,id),
    constraint uk_project_chart_version_number unique (
      workspace_id,space_id,chart_id,version_number
    ),
    constraint fk_project_chart_version_chart foreign key (workspace_id,space_id,chart_id)
      references project_chart_definitions(workspace_id,space_id,id),
    constraint fk_project_chart_version_actor foreign key (workspace_id,published_by)
      references users(workspace_id,id)
);

alter table project_chart_definitions
  add constraint fk_project_chart_definition_current_version
  foreign key (workspace_id,space_id,current_version_id)
  references project_chart_versions(workspace_id,space_id,id);

create table project_dashboard_versions (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    dashboard_id uuid not null,
    version_number integer not null check (version_number >= 1),
    schema_version integer not null check (schema_version = 1),
    definition_hash varchar(64) not null check (definition_hash ~ '^[0-9a-f]{64}$'),
    config jsonb not null check (jsonb_typeof(config) = 'object'),
    chart_version_refs jsonb not null check (jsonb_typeof(chart_version_refs) = 'array'),
    published_by uuid not null,
    published_at timestamptz not null default now(),
    constraint uk_project_dashboard_version_boundary unique (workspace_id,space_id,id),
    constraint uk_project_dashboard_version_number unique (
      workspace_id,space_id,dashboard_id,version_number
    ),
    constraint fk_project_dashboard_version_dashboard foreign key (
      workspace_id,space_id,dashboard_id
    ) references project_dashboards(workspace_id,space_id,id),
    constraint fk_project_dashboard_version_actor foreign key (workspace_id,published_by)
      references users(workspace_id,id)
);
create index idx_project_dashboard_version_hash
  on project_dashboard_versions(workspace_id,space_id,dashboard_id,definition_hash);

alter table project_dashboards
  add constraint fk_project_dashboard_current_version
  foreign key (workspace_id,space_id,current_version_id)
  references project_dashboard_versions(workspace_id,space_id,id);

create table project_dashboard_preferences (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    dashboard_id uuid not null,
    user_id uuid not null,
    compact boolean not null default false,
    filter_values jsonb not null default '{}'::jsonb check (jsonb_typeof(filter_values) = 'object'),
    row_version bigint not null check (row_version >= 1),
    updated_at timestamptz not null default now(),
    constraint uk_project_dashboard_preference unique (
      workspace_id,space_id,dashboard_id,user_id
    ),
    constraint fk_project_dashboard_preference_dashboard foreign key (
      workspace_id,space_id,dashboard_id
    ) references project_dashboards(workspace_id,space_id,id),
    constraint fk_project_dashboard_preference_user foreign key (workspace_id,user_id)
      references users(workspace_id,id)
);

create table project_dashboard_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    dashboard_id uuid not null,
    operation varchar(32) not null check (
      operation in (
        'save_dashboard','publish_dashboard','disable_dashboard','revise_dashboard',
        'archive_dashboard','share_dashboard','unshare_dashboard'
      )
    ),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    response_payload jsonb not null check (jsonb_typeof(response_payload) = 'object'),
    status varchar(16) not null check (status = 'completed'),
    created_at timestamptz not null default now(),
    constraint uk_project_dashboard_command unique (
      workspace_id,space_id,actor_id,operation,request_id
    ),
    constraint fk_project_dashboard_command_dashboard foreign key (
      workspace_id,space_id,dashboard_id
    ) references project_dashboards(workspace_id,space_id,id),
    constraint fk_project_dashboard_command_actor foreign key (workspace_id,actor_id)
      references users(workspace_id,id)
);

create table project_dashboard_query_cache (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    dashboard_id uuid not null,
    dashboard_version integer not null check (dashboard_version >= 1),
    permission_fingerprint varchar(64) not null check (permission_fingerprint ~ '^[0-9a-f]{64}$'),
    query_fingerprint varchar(64) not null check (query_fingerprint ~ '^[0-9a-f]{64}$'),
    result_status varchar(16) not null check (
      result_status in ('ready','unknown','no_sample','suppressed','stale','truncated')
    ),
    low_cardinality_shape jsonb not null check (jsonb_typeof(low_cardinality_shape) = 'object'),
    expires_at timestamptz not null,
    rebuilt_at timestamptz not null default now(),
    constraint uk_project_dashboard_query_cache unique (
      workspace_id,space_id,dashboard_id,dashboard_version,
      permission_fingerprint,query_fingerprint
    ),
    constraint fk_project_dashboard_query_cache_dashboard foreign key (
      workspace_id,space_id,dashboard_id
    ) references project_dashboards(workspace_id,space_id,id)
);
create index idx_project_dashboard_query_cache_expiry
  on project_dashboard_query_cache(workspace_id,space_id,expires_at);

create trigger trg_project_chart_version_immutable
before update or delete on project_chart_versions
for each row execute function guard_project_metric_immutable();

create trigger trg_project_dashboard_version_immutable
before update or delete on project_dashboard_versions
for each row execute function guard_project_metric_immutable();

create trigger trg_project_dashboard_command_immutable
before update or delete on project_dashboard_commands
for each row execute function guard_project_metric_immutable();
