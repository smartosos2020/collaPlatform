create table project_work_item_field_options (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    space_id uuid not null,
    type_definition_id uuid not null,
    field_definition_id uuid not null,
    option_key varchar(64) not null,
    name varchar(128) not null,
    color varchar(7) not null,
    sort_order integer not null default 0,
    status varchar(32) not null,
    created_by uuid not null,
    created_at timestamptz not null,
    updated_by uuid not null,
    updated_at timestamptz not null,
    constraint uk_project_work_item_field_options_field_key
        unique (workspace_id, space_id, type_definition_id, field_definition_id, option_key),
    constraint uk_project_work_item_field_options_scope_id
        unique (workspace_id, space_id, type_definition_id, field_definition_id, id),
    constraint fk_project_work_item_field_options_field_scope
        foreign key (workspace_id, space_id, type_definition_id, field_definition_id)
        references project_work_item_field_definitions(workspace_id, space_id, type_definition_id, id),
    constraint fk_project_work_item_field_options_created_by_workspace
        foreign key (workspace_id, created_by) references users(workspace_id, id),
    constraint fk_project_work_item_field_options_updated_by_workspace
        foreign key (workspace_id, updated_by) references users(workspace_id, id),
    constraint ck_project_work_item_field_options_key
        check (option_key ~ '^[a-z][a-z0-9_]*$'),
    constraint ck_project_work_item_field_options_name
        check (length(btrim(name)) between 1 and 128),
    constraint ck_project_work_item_field_options_color
        check (color ~ '^#[0-9A-F]{6}$'),
    constraint ck_project_work_item_field_options_sort_order check (sort_order >= 0),
    constraint ck_project_work_item_field_options_status
        check (status in ('active', 'disabled'))
);

create index idx_project_work_item_field_options_field_status_order
    on project_work_item_field_options
    (workspace_id, space_id, type_definition_id, field_definition_id, status, sort_order, id);

create or replace function protect_project_work_item_field_option_identity()
returns trigger as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'work item field options cannot be deleted';
    end if;
    if new.workspace_id <> old.workspace_id
       or new.space_id <> old.space_id
       or new.type_definition_id <> old.type_definition_id
       or new.field_definition_id <> old.field_definition_id
       or new.option_key <> old.option_key then
        raise exception 'work item field option identity is immutable';
    end if;
    return new;
end;
$$ language plpgsql;

create trigger trg_protect_project_work_item_field_option_identity
before update or delete on project_work_item_field_options
for each row execute function protect_project_work_item_field_option_identity();
