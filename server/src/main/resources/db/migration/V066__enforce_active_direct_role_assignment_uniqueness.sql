create unique index uk_role_assignments_active_direct_user
    on role_assignments (workspace_id, role_id, subject_id, scope_type)
    where subject_type = 'user'
      and status = 'active'
      and scope_id is null;
