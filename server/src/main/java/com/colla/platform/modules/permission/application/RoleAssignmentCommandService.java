package com.colla.platform.modules.permission.application;

import com.colla.platform.modules.permission.contract.RoleAssignmentCommands;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RoleAssignmentCommandService implements RoleAssignmentCommands {
    private final JdbcTemplate jdbcTemplate;

    public RoleAssignmentCommandService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void assign(UUID workspaceId, UUID userId, String roleCode, UUID actorId) {
        UUID roleId = jdbcTemplate.queryForObject(
            "select id from roles where workspace_id = ? and code = ?",
            UUID.class,
            workspaceId,
            roleCode
        );

        jdbcTemplate.update(
            """
                insert into role_assignments
                    (id, workspace_id, role_id, subject_type, subject_id, scope_type,
                     effective_at, status, created_by, created_at)
                values (?, ?, ?, 'user', ?, 'system', now(), 'active', ?, now())
                on conflict do nothing
                """,
            UUID.randomUUID(),
            workspaceId,
            roleId,
            userId,
            actorId
        );
        jdbcTemplate.update(
            """
                insert into user_roles (id, workspace_id, user_id, role_id, created_by, created_at)
                select ?, ?, ?, ?, ?, now()
                where not exists (
                    select 1
                    from user_roles
                    where workspace_id = ? and user_id = ? and role_id = ?
                )
                """,
            UUID.randomUUID(),
            workspaceId,
            userId,
            roleId,
            actorId,
            workspaceId,
            userId,
            roleId
        );
    }
}
