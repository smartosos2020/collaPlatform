package com.colla.platform.modules.permission.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

class RoleAssignmentCommandServiceTests {
    @Test
    void assignsTheCurrentRoleModelBeforeSynchronizingTheLegacyReadModel() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(
            "select id from roles where workspace_id = ? and code = ?",
            UUID.class,
            workspaceId,
            "admin"
        )).thenReturn(roleId);

        new RoleAssignmentCommandService(jdbcTemplate).assign(workspaceId, userId, "admin", actorId);

        InOrder order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).queryForObject(
            "select id from roles where workspace_id = ? and code = ?",
            UUID.class,
            workspaceId,
            "admin"
        );
        order.verify(jdbcTemplate).update(
            contains("insert into role_assignments"),
            any(UUID.class),
            eq(workspaceId),
            eq(roleId),
            eq(userId),
            eq(actorId)
        );
        order.verify(jdbcTemplate).update(
            contains("insert into user_roles"),
            any(UUID.class),
            eq(workspaceId),
            eq(userId),
            eq(roleId),
            eq(actorId),
            eq(workspaceId),
            eq(userId),
            eq(roleId)
        );
    }
}
