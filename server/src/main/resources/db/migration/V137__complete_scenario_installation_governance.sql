create table project_scenario_template_upgrade_diffs (
    id uuid primary key,
    workspace_id uuid not null,
    space_id uuid not null,
    installation_id uuid not null,
    run_id uuid not null references project_scenario_template_install_runs(id),
    base_manifest_hash varchar(64) not null check (base_manifest_hash ~ '^[0-9a-f]{64}$'),
    upstream_manifest_hash varchar(64) not null check (upstream_manifest_hash ~ '^[0-9a-f]{64}$'),
    local_manifest_hash varchar(64) not null check (local_manifest_hash ~ '^[0-9a-f]{64}$'),
    status varchar(24) not null check (status in ('unchanged','ready','conflicted','applied')),
    created_at timestamptz not null default now(),
    constraint uk_project_scenario_diff_run unique (run_id),
    constraint fk_project_scenario_diff_install foreign key (workspace_id,space_id,installation_id)
      references project_scenario_template_installations(workspace_id,space_id,id)
);

create table project_scenario_template_upgrade_conflicts (
    id uuid primary key,
    diff_id uuid not null references project_scenario_template_upgrade_diffs(id),
    key_path varchar(240) not null,
    reason varchar(64) not null,
    base_hash varchar(64) not null,
    upstream_hash varchar(64) not null,
    local_hash varchar(64) not null,
    resolution varchar(24) not null default '',
    resolved boolean not null default false,
    created_at timestamptz not null default now(),
    constraint uk_project_scenario_conflict_key unique (diff_id,key_path)
);

create index idx_project_scenario_installation_list
  on project_scenario_template_installations(workspace_id,space_id,updated_at desc,id);
create index idx_project_scenario_diff_installation
  on project_scenario_template_upgrade_diffs(workspace_id,space_id,installation_id,created_at desc);

create trigger trg_project_scenario_run_immutable
before update or delete on project_scenario_template_install_runs
for each row execute function guard_project_scenario_immutable();

create trigger trg_project_scenario_step_immutable
before update or delete on project_scenario_template_install_steps
for each row execute function guard_project_scenario_immutable();

create trigger trg_project_scenario_command_immutable
before update or delete on project_scenario_template_commands
for each row execute function guard_project_scenario_immutable();

create trigger trg_project_scenario_diff_immutable
before update or delete on project_scenario_template_upgrade_diffs
for each row execute function guard_project_scenario_immutable();

create trigger trg_project_scenario_conflict_immutable
before update or delete on project_scenario_template_upgrade_conflicts
for each row execute function guard_project_scenario_immutable();
