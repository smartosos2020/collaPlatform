package com.colla.platform.modules.permission.application;

import com.colla.platform.modules.permission.contract.KnowledgePermissionCommands;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class KnowledgePermissionCommandService implements KnowledgePermissionCommands {
    private final JdbcTemplate jdbcTemplate;

    public KnowledgePermissionCommandService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void copyInherited(UUID workspaceId, UUID itemId, UUID parentId, UUID actorId) {
        jdbcTemplate.update(
            """
                update resource_permissions
                set status = 'revoked', updated_by = ?, updated_at = now()
                where workspace_id = ?
                  and resource_type = 'knowledge_content'
                  and resource_id = ?
                  and source_type = 'inherited'
                  and status = 'active'
                """,
            actorId,
            workspaceId,
            itemId
        );
        if (parentId == null) {
            return;
        }
        List<Map<String, Object>> parentPermissions = jdbcTemplate.queryForList(
            """
                select subject_type, subject_id, permission_level, expires_at
                from resource_permissions rp
                where rp.workspace_id = ?
                  and rp.resource_type = 'knowledge_content'
                  and rp.resource_id = ?
                  and rp.status = 'active'
                  and (rp.expires_at is null or rp.expires_at > now())
                  and not exists (
                      select 1
                      from resource_permissions existing
                      where existing.workspace_id = rp.workspace_id
                        and existing.resource_type = 'knowledge_content'
                        and existing.resource_id = ?
                        and existing.subject_type = rp.subject_type
                        and existing.subject_id = rp.subject_id
                        and existing.status = 'active'
                  )
                """,
            workspaceId,
            parentId,
            itemId
        );
        for (Map<String, Object> permission : parentPermissions) {
            jdbcTemplate.update(
                """
                    insert into resource_permissions
                        (id, workspace_id, resource_type, resource_id, subject_type, subject_id, permission_level,
                         source_type, source_id, expires_at, status, created_by, created_at, updated_by, updated_at)
                    values (?, ?, 'knowledge_content', ?, ?, ?, ?, 'inherited', ?, ?, 'active', ?, now(), ?, now())
                    """,
                UUID.randomUUID(),
                workspaceId,
                itemId,
                permission.get("subject_type"),
                permission.get("subject_id"),
                permission.get("permission_level"),
                parentId,
                permission.get("expires_at"),
                actorId,
                actorId
            );
        }
    }

    @Override
    public void upsertDirect(
        UUID workspaceId,
        UUID itemId,
        String subjectType,
        UUID subjectId,
        String permissionLevel,
        UUID actorId
    ) {
        int updated = jdbcTemplate.update(
            """
                update resource_permissions
                set permission_level = ?, source_type = 'direct', source_id = null, status = 'active',
                    updated_by = ?, updated_at = now()
                where workspace_id = ? and resource_type = 'knowledge_content' and resource_id = ?
                  and subject_type = ? and subject_id = ?
                """,
            permissionLevel,
            actorId,
            workspaceId,
            itemId,
            subjectType,
            subjectId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                """
                    insert into resource_permissions
                        (id, workspace_id, resource_type, resource_id, subject_type, subject_id, permission_level,
                         source_type, source_id, status, created_by, created_at, updated_by, updated_at)
                    values (?, ?, 'knowledge_content', ?, ?, ?, ?, 'direct', null, 'active', ?, now(), ?, now())
                    """,
                UUID.randomUUID(),
                workspaceId,
                itemId,
                subjectType,
                subjectId,
                permissionLevel,
                actorId,
                actorId
            );
        }
    }
}
