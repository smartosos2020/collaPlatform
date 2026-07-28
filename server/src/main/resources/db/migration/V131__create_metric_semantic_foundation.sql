create table project_metric_definitions (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    metric_key varchar(64) not null check (metric_key ~ '^[a-z][a-z0-9_.-]{1,63}$'),
    name varchar(160) not null,
    description varchar(2000) not null default '',
    unit varchar(16) not null check (unit in ('count','hours','days','percent','points')),
    status varchar(16) not null check (status in ('draft','active','disabled','archived')),
    row_version bigint not null check (row_version >= 1),
    draft_expression jsonb not null check (jsonb_typeof(draft_expression) = 'object'),
    draft_window jsonb not null check (jsonb_typeof(draft_window) = 'object'),
    current_version_id uuid,
    created_by uuid not null,
    updated_by uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_project_metric_definition_boundary unique (workspace_id,space_id,id),
    constraint uk_project_metric_definition_key unique (workspace_id,space_id,metric_key),
    constraint fk_project_metric_definition_space foreign key (workspace_id,space_id)
      references project_spaces(workspace_id,id),
    constraint fk_project_metric_definition_creator foreign key (workspace_id,created_by)
      references users(workspace_id,id),
    constraint fk_project_metric_definition_updater foreign key (workspace_id,updated_by)
      references users(workspace_id,id)
);
create index idx_project_metric_definition_list
  on project_metric_definitions(workspace_id,space_id,status,updated_at desc,id);

create table project_metric_versions (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    metric_id uuid not null,
    version_number integer not null check (version_number >= 1),
    schema_version integer not null check (schema_version = 1),
    definition_hash varchar(64) not null check (definition_hash ~ '^[0-9a-f]{64}$'),
    expression jsonb not null check (jsonb_typeof(expression) = 'object'),
    window_definition jsonb not null check (jsonb_typeof(window_definition) = 'object'),
    published_by uuid not null,
    published_at timestamptz not null default now(),
    constraint uk_project_metric_version_boundary unique (workspace_id,space_id,id),
    constraint uk_project_metric_version_number unique (workspace_id,space_id,metric_id,version_number),
    constraint fk_project_metric_version_metric foreign key (workspace_id,space_id,metric_id)
      references project_metric_definitions(workspace_id,space_id,id),
    constraint fk_project_metric_version_actor foreign key (workspace_id,published_by)
      references users(workspace_id,id)
);
create index idx_project_metric_version_hash
  on project_metric_versions(workspace_id,space_id,metric_id,definition_hash);

alter table project_metric_definitions
  add constraint fk_project_metric_definition_current_version
  foreign key (workspace_id,space_id,current_version_id)
  references project_metric_versions(workspace_id,space_id,id);

create table project_metric_dimensions (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    dimension_key varchar(64) not null check (dimension_key ~ '^[a-z][a-z0-9_.-]{1,63}$'),
    version integer not null check (version >= 1),
    label varchar(120) not null,
    value_type varchar(16) not null check (value_type in ('string','uuid','date','boolean')),
    source_contract varchar(200) not null,
    cardinality_limit integer not null check (cardinality_limit between 1 and 1000),
    status varchar(16) not null check (status in ('active','retired')),
    created_at timestamptz not null default now(),
    constraint uk_project_metric_dimension unique (workspace_id,space_id,dimension_key,version),
    constraint fk_project_metric_dimension_space foreign key (workspace_id,space_id)
      references project_spaces(workspace_id,id)
);
create index idx_project_metric_dimension_catalog
  on project_metric_dimensions(workspace_id,space_id,status,dimension_key,version desc);

insert into project_metric_dimensions(
  id,workspace_id,space_id,dimension_key,version,label,value_type,
  source_contract,cardinality_limit,status
)
select gen_random_uuid(),s.workspace_id,s.id,d.dimension_key,1,d.label,d.value_type,
       d.source_contract,d.cardinality_limit,'active'
  from project_spaces s
 cross join (values
   ('status','状态','string','WorkItemQueryService.authorizedFacet',32),
   ('type','工作项类型','string','WorkItemQueryService.authorizedFacet',64),
   ('calendar_day','日历日','date','MetricSemanticService.window',366),
   ('space','受权空间','uuid','CrossSpaceGrantService.authorizedSpaceReference',50)
 ) as d(dimension_key,label,value_type,source_contract,cardinality_limit);

create table project_metric_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    metric_id uuid not null,
    operation varchar(24) not null check (
      operation in ('save_metric','publish_metric','disable_metric','revise_metric','archive_metric')
    ),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    response_payload jsonb not null check (jsonb_typeof(response_payload) = 'object'),
    status varchar(16) not null check (status = 'completed'),
    created_at timestamptz not null default now(),
    constraint uk_project_metric_command unique (workspace_id,space_id,actor_id,operation,request_id),
    constraint fk_project_metric_command_space foreign key (workspace_id,space_id)
      references project_spaces(workspace_id,id),
    constraint fk_project_metric_command_actor foreign key (workspace_id,actor_id)
      references users(workspace_id,id),
    constraint fk_project_metric_command_metric foreign key (workspace_id,space_id,metric_id)
      references project_metric_definitions(workspace_id,space_id,id)
);

create table project_metric_result_index (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    metric_id uuid not null,
    metric_version integer not null check (metric_version >= 1),
    window_start timestamptz not null,
    window_end timestamptz not null,
    result_status varchar(16) not null check (
      result_status in ('ready','unknown','no_sample','suppressed','stale','truncated')
    ),
    result_value numeric,
    numerator numeric,
    denominator numeric,
    source_versions jsonb not null check (jsonb_typeof(source_versions) = 'array'),
    sample_count integer not null check (sample_count between 0 and 100000),
    expires_at timestamptz not null,
    rebuilt_at timestamptz not null default now(),
    constraint ck_project_metric_result_window check (window_start < window_end),
    constraint ck_project_metric_result_value check (
      (result_status = 'ready' and result_value is not null)
      or (result_status <> 'ready' and result_value is null)
    ),
    constraint uk_project_metric_result unique (
      workspace_id,space_id,metric_id,metric_version,window_start,window_end
    ),
    constraint fk_project_metric_result_metric foreign key (workspace_id,space_id,metric_id)
      references project_metric_definitions(workspace_id,space_id,id)
);
create index idx_project_metric_result_expiry
  on project_metric_result_index(workspace_id,space_id,expires_at);

create function guard_project_metric_immutable()
returns trigger language plpgsql as $$
begin
  if current_setting('colla.project_space_cleanup', true) = 'on' and tg_op = 'DELETE' then
    return old;
  end if;
  raise exception 'metric immutable fact cannot be changed' using errcode='23514';
end;
$$;

create trigger trg_project_metric_version_immutable
before update or delete on project_metric_versions
for each row execute function guard_project_metric_immutable();

create trigger trg_project_metric_command_immutable
before update or delete on project_metric_commands
for each row execute function guard_project_metric_immutable();

create trigger trg_project_metric_dimension_immutable
before update or delete on project_metric_dimensions
for each row execute function guard_project_metric_immutable();
