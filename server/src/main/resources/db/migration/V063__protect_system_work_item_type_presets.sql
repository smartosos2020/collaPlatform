create or replace function guard_project_work_item_type_identity()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        if current_setting('colla.project_space_cleanup', true) = 'on' then
            return old;
        end if;
        raise exception 'work item type definitions cannot be physically deleted' using errcode = '23514';
    end if;
    if new.workspace_id <> old.workspace_id
        or new.space_id <> old.space_id
        or new.type_key <> old.type_key
        or new.is_system <> old.is_system then
        raise exception 'work item type identity is immutable' using errcode = '23514';
    end if;
    if old.is_system and (
        new.name <> old.name
        or new.icon <> old.icon
        or new.description <> old.description
        or new.current_version_id <> old.current_version_id
        or new.status = 'retired'
    ) then
        raise exception 'system work item type definition is protected' using errcode = '23514';
    end if;
    if old.status = 'retired' and new.status <> old.status then
        raise exception 'retired work item types cannot transition' using errcode = '23514';
    end if;
    return new;
end;
$$;

create or replace function guard_project_work_item_type_version_immutability()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' and current_setting('colla.project_space_cleanup', true) = 'on' then
        return old;
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
