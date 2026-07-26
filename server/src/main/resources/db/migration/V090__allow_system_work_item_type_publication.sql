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
