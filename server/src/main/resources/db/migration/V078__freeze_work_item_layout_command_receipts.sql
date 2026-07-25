alter table project_work_item_layout_commands
    add column response_schema_version smallint,
    add column response_aggregate_version bigint,
    add column response_config_hash varchar(64),
    add column response_payload jsonb;

alter table project_work_item_layout_commands
    add constraint ck_project_work_item_layout_commands_response_schema
        check (response_schema_version is null or response_schema_version = 1),
    add constraint ck_project_work_item_layout_commands_response_version
        check (response_aggregate_version is null or response_aggregate_version >= 0),
    add constraint ck_project_work_item_layout_commands_response_hash
        check (response_config_hash is null or response_config_hash ~ '^[0-9a-f]{64}$'),
    add constraint ck_project_work_item_layout_commands_response_payload
        check (response_payload is null or jsonb_typeof(response_payload) = 'object'),
    add constraint ck_project_work_item_layout_commands_receipt
        check (
            (
                response_schema_version is null
                and response_aggregate_version is null
                and response_config_hash is null
                and response_payload is null
            )
            or (
                response_schema_version = 1
                and (
                    (
                        status = 'pending'
                        and response_layout_id is null
                        and response_aggregate_version is null
                        and response_config_hash is null
                        and response_payload is null
                    )
                    or (
                        status = 'completed'
                        and response_layout_id is not null
                        and response_aggregate_version is not null
                        and response_config_hash is not null
                        and response_payload is not null
                    )
                )
            )
        );

create function guard_project_work_item_layout_command_receipt()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'work item layout command receipts cannot be physically deleted'
            using errcode = '23514';
    end if;
    if old.status = 'completed' then
        raise exception 'completed work item layout command receipts are immutable'
            using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.request_id <> old.request_id
        or new.operation <> old.operation
        or new.request_hash <> old.request_hash
        or new.created_by <> old.created_by
        or new.created_at <> old.created_at
        or new.response_schema_version is distinct from old.response_schema_version then
        raise exception 'work item layout command identity is immutable'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger trg_project_work_item_layout_command_receipt
before update or delete on project_work_item_layout_commands
for each row execute function guard_project_work_item_layout_command_receipt();

comment on column project_work_item_layout_commands.response_schema_version is
    'Null identifies a pre-V078 legacy receipt that cannot safely replay an original response.';
comment on column project_work_item_layout_commands.response_payload is
    'Immutable application response snapshot used for exact request-id replay.';
