package com.colla.platform.modules.identity.application;

import com.colla.platform.modules.identity.contract.IdentityGovernance;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdentityGovernanceService implements IdentityGovernance {
    private final JdbcTemplate jdbcTemplate;
    private final IdentitySecurityChangePublisher securityChanges;

    public IdentityGovernanceService(
        JdbcTemplate jdbcTemplate,
        IdentitySecurityChangePublisher securityChanges
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.securityChanges = securityChanges;
    }

    @Override
    public boolean isActive(UUID workspaceId, Resource resource, UUID targetId) {
        Integer count = jdbcTemplate.queryForObject(
            switch (resource) {
                case MEMBER -> "select count(*) from users where id = ? and workspace_id = ? and deleted_at is null";
                case DEPARTMENT -> "select count(*) from departments where id = ? and workspace_id = ? and deleted_at is null";
                case USER_GROUP -> "select count(*) from user_groups where id = ? and workspace_id = ? and deleted_at is null";
            },
            Integer.class,
            targetId,
            workspaceId
        );
        return count != null && count > 0;
    }

    @Override
    public int disable(UUID workspaceId, UUID actorId, Resource resource, UUID targetId) {
        int changed = jdbcTemplate.update(
            switch (resource) {
                case MEMBER -> "update users set status = 'disabled', updated_by = ?, updated_at = now() where id = ? and workspace_id = ? and deleted_at is null";
                case DEPARTMENT -> "update departments set status = 'disabled', updated_by = ?, updated_at = now() where id = ? and workspace_id = ? and deleted_at is null";
                case USER_GROUP -> "update user_groups set status = 'disabled', updated_by = ?, updated_at = now() where id = ? and workspace_id = ? and deleted_at is null";
            },
            actorId,
            targetId,
            workspaceId
        );
        if (changed > 0) {
            String aggregateType = switch (resource) {
                case MEMBER -> "user";
                case DEPARTMENT -> "department";
                case USER_GROUP -> "user_group";
            };
            String calibrationPath = switch (resource) {
                case MEMBER -> "/api/admin/users";
                case DEPARTMENT -> "/api/admin/departments";
                case USER_GROUP -> "/api/admin/user-groups";
            };
            securityChanges.publish(
                workspaceId,
                actorId,
                aggregateType,
                targetId,
                calibrationPath,
                "governance-disable:" + aggregateType + ":" + targetId
            );
        }
        return changed;
    }
}
