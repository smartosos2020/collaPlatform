-- Project-space migration rollback owns the space it created. Immutable configuration rows
-- remain protected during normal commands, but may be removed inside the transaction-scoped
-- cleanup path used by JdbcProjectSpaceRepository.

create or replace function guard_project_work_item_type_version_immutability()
returns trigger
language plpgsql
as $$
begin
    if current_setting('colla.project_space_cleanup', true) = 'on' then
        return case when tg_op = 'DELETE' then old else new end;
    end if;
    if tg_op = 'DELETE' and old.status in ('published', 'superseded') then
        raise exception 'published work item type versions are immutable' using errcode = '23514';
    end if;
    if tg_op = 'UPDATE' and old.status = 'published'
        and new.status = 'superseded'
        and new.id = old.id
        and new.workspace_id = old.workspace_id
        and new.space_id = old.space_id
        and new.type_definition_id = old.type_definition_id
        and new.version_number = old.version_number
        and new.config_hash = old.config_hash
        and new.config = old.config
        and new.snapshot_schema_version = old.snapshot_schema_version
        and new.source_draft_id is not distinct from old.source_draft_id
        and new.rollback_source_version_id is not distinct from old.rollback_source_version_id
        and new.created_by = old.created_by
        and new.created_at = old.created_at
        and new.published_by = old.published_by
        and new.published_at = old.published_at then
        return new;
    end if;
    if tg_op = 'UPDATE' and old.status in ('published', 'superseded') then
        raise exception 'published work item type versions are immutable' using errcode = '23514';
    end if;
    return case when tg_op = 'DELETE' then old else new end;
end;
$$;

create or replace function guard_project_work_item_configuration_draft_identity()
returns trigger
language plpgsql
as $$
begin
    if current_setting('colla.project_space_cleanup', true) = 'on' then
        return case when tg_op = 'DELETE' then old else new end;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'work item configuration drafts cannot be physically deleted'
            using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.source_legacy_version_id is distinct from old.source_legacy_version_id
        or new.source_version_id is distinct from old.source_version_id
        or new.lineage_kind <> old.lineage_kind
        or new.created_by <> old.created_by
        or new.created_at <> old.created_at then
        raise exception 'work item configuration draft identity is immutable'
            using errcode = '23514';
    end if;
    if old.status = 'abandoned' and new is distinct from old then
        raise exception 'abandoned work item configuration drafts are immutable'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create or replace function guard_project_work_item_configuration_draft_command_receipt()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'work item configuration draft command receipts cannot be physically deleted'
            using errcode = '23514';
    end if;
    if old.status = 'completed' then
        raise exception 'completed work item configuration draft command receipts are immutable'
            using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.request_id <> old.request_id
        or new.operation <> old.operation
        or new.request_hash <> old.request_hash
        or new.response_schema_version <> old.response_schema_version
        or new.created_by <> old.created_by
        or new.created_at <> old.created_at then
        raise exception 'work item configuration draft command identity is immutable'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create or replace function guard_project_work_item_configuration_publication_receipt()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'configuration publication receipts cannot be physically deleted'
            using errcode = '23514';
    end if;
    if old.status = 'completed' then
        raise exception 'completed configuration publication receipts are immutable'
            using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.request_id <> old.request_id
        or new.operation <> old.operation
        or new.request_hash <> old.request_hash
        or new.response_schema_version <> old.response_schema_version
        or new.created_by <> old.created_by
        or new.created_at <> old.created_at then
        raise exception 'configuration publication receipt identity is immutable'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create or replace function guard_project_work_item_field_identity()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'work item field definitions cannot be physically deleted' using errcode = '23514';
    end if;
    if new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.field_key <> old.field_key
        or new.field_type <> old.field_type
        or new.is_system <> old.is_system then
        raise exception 'work item field identity is immutable' using errcode = '23514';
    end if;
    if old.status = 'retired' and new.status <> old.status then
        raise exception 'retired work item fields cannot transition' using errcode = '23514';
    end if;
    return new;
end;
$$;

create or replace function protect_project_work_item_field_option_identity()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
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
$$;

create or replace function guard_project_work_item_layout_identity()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'work item layouts cannot be physically deleted' using errcode = '23514';
    end if;
    if new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.layout_kind <> old.layout_kind then
        raise exception 'work item layout identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create or replace function guard_project_work_item_layout_node_identity()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'work item layout nodes cannot be physically deleted' using errcode = '23514';
    end if;
    if new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.layout_id <> old.layout_id
        or new.node_key <> old.node_key
        or new.node_type <> old.node_type
        or new.field_id is distinct from old.field_id
        or new.field_key is distinct from old.field_key then
        raise exception 'work item layout node identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create or replace function guard_project_work_item_field_policy_identity()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'work item field access policies cannot be physically deleted' using errcode = '23514';
    end if;
    if new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.layout_id <> old.layout_id
        or new.field_id <> old.field_id
        or new.field_key <> old.field_key
        or new.policy_key <> old.policy_key then
        raise exception 'work item field access policy identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

create or replace function guard_project_work_item_layout_command_receipt()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
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

create or replace function guard_project_configuration_template_receipt()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
    end if;
    if tg_op = 'DELETE' then
        raise exception 'configuration template receipts cannot be deleted' using errcode = '23514';
    end if;
    if old.status = 'completed' then
        raise exception 'completed configuration template receipts are immutable' using errcode = '23514';
    end if;
    if new.id <> old.id
        or new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_definition_id <> old.type_definition_id
        or new.request_id <> old.request_id
        or new.operation <> old.operation
        or new.request_hash <> old.request_hash
        or new.created_by <> old.created_by
        or new.created_at <> old.created_at then
        raise exception 'configuration template receipt identity is immutable' using errcode = '23514';
    end if;
    return new;
end;
$$;

comment on function guard_project_work_item_configuration_draft_identity() is
    'Preserves immutable draft identity except during transaction-scoped project-space rollback cleanup.';
