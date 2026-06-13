insert into roles (id, workspace_id, code, name, scope, is_builtin, created_at, updated_at)
values
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001', 'admin', 'Administrator', 'system', true, now(), now()),
    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000001', 'member', 'Member', 'system', true, now(), now()),
    ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000001', 'project_owner', 'Project Owner', 'project', true, now(), now()),
    ('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000001', 'project_member', 'Project Member', 'project', true, now(), now()),
    ('00000000-0000-0000-0000-000000000105', '00000000-0000-0000-0000-000000000001', 'project_viewer', 'Project Viewer', 'project', true, now(), now())
on conflict (workspace_id, code) do nothing;

insert into permissions (id, code, name, module, created_at)
values
    ('00000000-0000-0000-0000-000000000201', 'admin.access', 'Access admin console', 'admin', now()),
    ('00000000-0000-0000-0000-000000000202', 'user.manage', 'Manage users', 'identity', now()),
    ('00000000-0000-0000-0000-000000000203', 'project.create', 'Create projects', 'project', now()),
    ('00000000-0000-0000-0000-000000000204', 'project.manage', 'Manage projects', 'project', now()),
    ('00000000-0000-0000-0000-000000000205', 'issue.create', 'Create issues', 'project', now()),
    ('00000000-0000-0000-0000-000000000206', 'issue.update', 'Update issues', 'project', now()),
    ('00000000-0000-0000-0000-000000000207', 'doc.create', 'Create documents', 'doc', now()),
    ('00000000-0000-0000-0000-000000000208', 'doc.update', 'Update documents', 'doc', now()),
    ('00000000-0000-0000-0000-000000000209', 'base.create', 'Create bases', 'base', now()),
    ('00000000-0000-0000-0000-000000000210', 'base.update', 'Update bases', 'base', now())
on conflict (code) do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
cross join permissions p
where r.workspace_id = '00000000-0000-0000-0000-000000000001'
  and r.code = 'admin'
on conflict (role_id, permission_id) do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('project.create', 'issue.create', 'issue.update', 'doc.create', 'doc.update', 'base.create', 'base.update')
where r.workspace_id = '00000000-0000-0000-0000-000000000001'
  and r.code = 'member'
on conflict (role_id, permission_id) do nothing;
