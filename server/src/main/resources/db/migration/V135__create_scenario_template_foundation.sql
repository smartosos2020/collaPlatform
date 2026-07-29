create table project_scenario_templates (
    id uuid primary key,
    scenario_key varchar(96) not null unique
        check (scenario_key ~ '^[a-z][a-z0-9_.-]{1,95}$'),
    name varchar(160) not null,
    description varchar(2000) not null default '',
    status varchar(16) not null check (status in ('active','withdrawn')),
    current_version_id uuid,
    aggregate_version bigint not null check (aggregate_version >= 1),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table project_scenario_template_versions (
    id uuid primary key,
    template_id uuid not null references project_scenario_templates(id),
    version_number integer not null check (version_number >= 1),
    schema_version integer not null check (schema_version = 1),
    manifest_hash varchar(64) not null check (manifest_hash ~ '^[0-9a-f]{64}$'),
    manifest jsonb not null check (jsonb_typeof(manifest) = 'object'),
    catalog_version varchar(64) not null,
    published_at timestamptz not null,
    constraint uk_project_scenario_template_version unique (template_id,version_number),
    constraint uk_project_scenario_template_hash unique (template_id,manifest_hash)
);

alter table project_scenario_templates
    add constraint fk_project_scenario_template_current_version
    foreign key (current_version_id)
    references project_scenario_template_versions(id);

create table project_scenario_template_components (
    template_version_id uuid not null references project_scenario_template_versions(id),
    component_key varchar(96) not null
        check (component_key ~ '^[a-z][a-z0-9_.-]{1,95}$'),
    component_kind varchar(32) not null check (
      component_kind in (
        'work_item_type','relation','saved_view','board','project_plan',
        'automation','risk_policy','metric','dashboard'
      )
    ),
    owner_contract varchar(160) not null,
    configuration_template_key varchar(128) not null default '',
    dependency_keys jsonb not null check (jsonb_typeof(dependency_keys) = 'array'),
    required boolean not null,
    description varchar(1000) not null default '',
    sort_order integer not null check (sort_order between 0 and 63),
    primary key (template_version_id,component_key)
);

create table project_scenario_template_installations (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    template_id uuid not null references project_scenario_templates(id),
    installed_version_id uuid not null references project_scenario_template_versions(id),
    upstream_version_id uuid not null references project_scenario_template_versions(id),
    status varchar(24) not null check (
      status in ('planning','installing','installed','attention','detached')
    ),
    local_manifest_hash varchar(64) not null check (local_manifest_hash ~ '^[0-9a-f]{64}$'),
    aggregate_version bigint not null check (aggregate_version >= 1),
    installed_by uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_project_scenario_install_boundary unique (workspace_id,space_id,id),
    constraint uk_project_scenario_install_template unique (workspace_id,space_id,template_id),
    constraint fk_project_scenario_install_space foreign key (workspace_id,space_id)
      references project_spaces(workspace_id,id),
    constraint fk_project_scenario_install_actor foreign key (workspace_id,installed_by)
      references users(workspace_id,id)
);

create table project_scenario_template_install_runs (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    installation_id uuid,
    template_id uuid not null references project_scenario_templates(id),
    template_version_id uuid not null references project_scenario_template_versions(id),
    operation varchar(24) not null check (
      operation in ('dry_run','install','upgrade','retry','detach')
    ),
    status varchar(24) not null check (
      status in ('planned','running','completed','failed','attention')
    ),
    manifest_hash varchar(64) not null check (manifest_hash ~ '^[0-9a-f]{64}$'),
    diagnostic_code varchar(64) not null default '',
    requested_by uuid not null,
    started_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_project_scenario_run_boundary unique (workspace_id,space_id,id),
    constraint fk_project_scenario_run_space foreign key (workspace_id,space_id)
      references project_spaces(workspace_id,id),
    constraint fk_project_scenario_run_install foreign key (workspace_id,space_id,installation_id)
      references project_scenario_template_installations(workspace_id,space_id,id),
    constraint fk_project_scenario_run_actor foreign key (workspace_id,requested_by)
      references users(workspace_id,id)
);
create index idx_project_scenario_run_list
  on project_scenario_template_install_runs(workspace_id,space_id,started_at desc,id);

create table project_scenario_template_install_steps (
    id uuid primary key,
    run_id uuid not null references project_scenario_template_install_runs(id),
    component_key varchar(96) not null,
    step_order integer not null check (step_order between 0 and 63),
    owner_contract varchar(160) not null,
    operation varchar(32) not null,
    status varchar(24) not null check (
      status in ('planned','running','completed','failed','skipped')
    ),
    source_version varchar(160) not null default '',
    target_identity varchar(160) not null default '',
    target_version varchar(160) not null default '',
    diagnostic_code varchar(64) not null default '',
    started_at timestamptz,
    completed_at timestamptz,
    constraint uk_project_scenario_step unique (run_id,component_key,operation)
);

create table project_scenario_template_commands (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    actor_id uuid not null,
    operation varchar(24) not null check (
      operation in ('dry_run','install','upgrade','retry','detach')
    ),
    request_id varchar(120) not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    object_id uuid not null,
    response_payload jsonb not null,
    status varchar(16) not null check (status in ('started','completed','failed')),
    created_at timestamptz not null default now(),
    completed_at timestamptz,
    constraint uk_project_scenario_command unique (
      workspace_id,space_id,actor_id,operation,request_id
    ),
    constraint fk_project_scenario_command_space foreign key (workspace_id,space_id)
      references project_spaces(workspace_id,id),
    constraint fk_project_scenario_command_actor foreign key (workspace_id,actor_id)
      references users(workspace_id,id)
);

create function guard_project_scenario_immutable()
returns trigger language plpgsql as $$
begin
  raise exception 'project scenario template history is immutable';
end;
$$;

create trigger trg_project_scenario_version_immutable
before update or delete on project_scenario_template_versions
for each row execute function guard_project_scenario_immutable();
