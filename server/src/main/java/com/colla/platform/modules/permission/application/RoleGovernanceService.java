package com.colla.platform.modules.permission.application;

import com.colla.platform.modules.permission.contract.RoleGovernance;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RoleGovernanceService implements RoleGovernance {
    private final JdbcTemplate jdbcTemplate;
    private final PermissionSecurityChangePublisher securityChanges;

    public RoleGovernanceService(JdbcTemplate jdbcTemplate, PermissionSecurityChangePublisher securityChanges) {
        this.jdbcTemplate = jdbcTemplate;
        this.securityChanges = securityChanges;
    }

    @Override
    public boolean isActive(UUID workspaceId, UUID roleId) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from roles where id = ? and workspace_id = ? and status = 'active'",
            Integer.class,
            roleId,
            workspaceId
        );
        return count != null && count > 0;
    }

    @Override
    public int disable(UUID workspaceId, UUID actorId, UUID roleId) {
        int changed = jdbcTemplate.update(
            "update roles set status = 'disabled', updated_at = now() where id = ? and workspace_id = ? and status = 'active'",
            roleId,
            workspaceId
        );
        if (changed > 0) {
            securityChanges.publish(
                workspaceId, actorId, "role", roleId, "role", "/api/admin/roles/" + roleId,
                "governance-disable:" + roleId
            );
        }
        return changed;
    }
}
