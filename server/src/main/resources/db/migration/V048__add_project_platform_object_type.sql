insert into object_type_rules (id, object_type, web_path_pattern, deep_link_pattern, created_at)
values ('00000000-0000-0000-0000-000000000207', 'project', '/projects/{id}', 'colla://project/{id}', now())
on conflict (object_type) do nothing;
