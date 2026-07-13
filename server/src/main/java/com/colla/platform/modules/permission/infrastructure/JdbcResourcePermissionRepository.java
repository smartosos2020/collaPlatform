package com.colla.platform.modules.permission.infrastructure;

import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionGrant;
import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionEntry;
import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionMatch;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcResourcePermissionRepository implements ResourcePermissionRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcResourcePermissionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsertGrant(ResourcePermissionGrant grant) {
        int updated = jdbcTemplate.update(
            """
                update resource_permissions
                set permission_level = ?,
                    source_type = ?,
                    source_id = ?,
                    expires_at = ?,
                    status = 'active',
                    updated_by = ?,
                    updated_at = now()
                where workspace_id = ?
                  and resource_type = ?
                  and resource_id = ?
                  and subject_type = ?
                  and subject_id = ?
                  and status = 'active'
                """,
            grant.permissionLevel(),
            grant.sourceType(),
            grant.sourceId(),
            timestamp(grant.expiresAt()),
            grant.actorId(),
            grant.workspaceId(),
            grant.resourceType(),
            grant.resourceId(),
            grant.subjectType(),
            grant.subjectId()
        );
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update(
            """
                insert into resource_permissions
                    (id, workspace_id, resource_type, resource_id, subject_type, subject_id,
                     permission_level, source_type, source_id, expires_at, status, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active', ?, now(), ?, now())
                """,
            UUID.randomUUID(),
            grant.workspaceId(),
            grant.resourceType(),
            grant.resourceId(),
            grant.subjectType(),
            grant.subjectId(),
            grant.permissionLevel(),
            grant.sourceType(),
            grant.sourceId(),
            timestamp(grant.expiresAt()),
            grant.actorId(),
            grant.actorId()
        );
    }

    @Override
    public List<ResourcePermissionEntry> listGrants(UUID workspaceId, String resourceType, UUID resourceId) {
        return jdbcTemplate.query(
            """
                select rp.id, rp.resource_type, rp.resource_id, rp.subject_type, rp.subject_id,
                       coalesce(u.display_name, d.name, ug.name, r.name) subject_name,
                       coalesce(u.username, d.code, ug.code, r.code) subject_detail,
                       rp.permission_level, rp.source_type, rp.source_id, rp.expires_at, rp.status,
                       case
                           when rp.status <> 'active' then rp.status
                           when rp.expires_at is not null and rp.expires_at <= now() then 'expired'
                           else 'active'
                       end effective_status,
                       case rp.subject_type
                           when 'user' then 1
                           when 'department' then (
                               select count(distinct dm.user_id)
                               from department_members dm
                               where dm.workspace_id = rp.workspace_id
                                 and dm.department_id = rp.subject_id
                                 and dm.ended_at is null
                           )
                           when 'user_group' then (
                               select count(distinct expanded.user_id)
                               from (
                                   select ugm.subject_id user_id
                                   from user_group_members ugm
                                   where ugm.workspace_id = rp.workspace_id
                                     and ugm.group_id = rp.subject_id
                                     and ugm.subject_type = 'user'
                                     and ugm.removed_at is null
                                   union all
                                   select dm.user_id
                                   from user_group_members ugm
                                   join department_members dm on dm.workspace_id = ugm.workspace_id
                                       and dm.department_id = ugm.subject_id
                                       and dm.ended_at is null
                                   where ugm.workspace_id = rp.workspace_id
                                     and ugm.group_id = rp.subject_id
                                     and ugm.subject_type = 'department'
                                     and ugm.removed_at is null
                               ) expanded
                           )
                           when 'role' then (
                               select count(distinct ur.user_id)
                               from user_roles ur
                               where ur.workspace_id = rp.workspace_id
                                 and ur.role_id = rp.subject_id
                           )
                           else 0
                       end expanded_member_count,
                       rp.created_at, rp.updated_at
                from resource_permissions rp
                left join users u on rp.subject_type = 'user' and u.id = rp.subject_id and u.workspace_id = rp.workspace_id and u.deleted_at is null
                left join departments d on rp.subject_type = 'department' and d.id = rp.subject_id and d.workspace_id = rp.workspace_id and d.deleted_at is null
                left join user_groups ug on rp.subject_type = 'user_group' and ug.id = rp.subject_id and ug.workspace_id = rp.workspace_id and ug.deleted_at is null
                left join roles r on rp.subject_type = 'role' and r.id = rp.subject_id and r.workspace_id = rp.workspace_id
                where rp.workspace_id = ?
                  and rp.resource_type = ?
                  and rp.resource_id = ?
                order by
                  case when rp.status = 'active' and (rp.expires_at is null or rp.expires_at > now()) then 0 else 1 end,
                  case rp.source_type when 'direct' then 0 when 'owner' then 1 when 'inherited' then 2 else 3 end,
                  rp.created_at desc
                """,
            this::mapEntry,
            workspaceId,
            resourceType,
            resourceId
        );
    }

    @Override
    public Optional<ResourcePermissionEntry> findGrant(UUID workspaceId, UUID permissionId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select rp.id, rp.resource_type, rp.resource_id, rp.subject_type, rp.subject_id,
                           coalesce(u.display_name, d.name, ug.name, r.name) subject_name,
                           coalesce(u.username, d.code, ug.code, r.code) subject_detail,
                           rp.permission_level, rp.source_type, rp.source_id, rp.expires_at, rp.status,
                       case
                           when rp.status <> 'active' then rp.status
                           when rp.expires_at is not null and rp.expires_at <= now() then 'expired'
                           else 'active'
                       end effective_status,
                       case rp.subject_type
                           when 'user' then 1
                           when 'department' then (
                               select count(distinct dm.user_id)
                               from department_members dm
                               where dm.workspace_id = rp.workspace_id
                                 and dm.department_id = rp.subject_id
                                 and dm.ended_at is null
                           )
                           when 'user_group' then (
                               select count(distinct expanded.user_id)
                               from (
                                   select ugm.subject_id user_id
                                   from user_group_members ugm
                                   where ugm.workspace_id = rp.workspace_id
                                     and ugm.group_id = rp.subject_id
                                     and ugm.subject_type = 'user'
                                     and ugm.removed_at is null
                                   union all
                                   select dm.user_id
                                   from user_group_members ugm
                                   join department_members dm on dm.workspace_id = ugm.workspace_id
                                       and dm.department_id = ugm.subject_id
                                       and dm.ended_at is null
                                   where ugm.workspace_id = rp.workspace_id
                                     and ugm.group_id = rp.subject_id
                                     and ugm.subject_type = 'department'
                                     and ugm.removed_at is null
                               ) expanded
                           )
                           when 'role' then (
                               select count(distinct ur.user_id)
                               from user_roles ur
                               where ur.workspace_id = rp.workspace_id
                                 and ur.role_id = rp.subject_id
                           )
                           else 0
                       end expanded_member_count,
                       rp.created_at, rp.updated_at
                    from resource_permissions rp
                    left join users u on rp.subject_type = 'user' and u.id = rp.subject_id and u.workspace_id = rp.workspace_id and u.deleted_at is null
                    left join departments d on rp.subject_type = 'department' and d.id = rp.subject_id and d.workspace_id = rp.workspace_id and d.deleted_at is null
                    left join user_groups ug on rp.subject_type = 'user_group' and ug.id = rp.subject_id and ug.workspace_id = rp.workspace_id and ug.deleted_at is null
                    left join roles r on rp.subject_type = 'role' and r.id = rp.subject_id and r.workspace_id = rp.workspace_id
                    where rp.workspace_id = ? and rp.id = ?
                    """,
                this::mapEntry,
                workspaceId,
                permissionId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean revokeGrant(UUID workspaceId, UUID permissionId, UUID actorId) {
        return jdbcTemplate.update(
            """
                update resource_permissions
                set status = 'revoked', updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and status = 'active'
                """,
            actorId,
            workspaceId,
            permissionId
        ) > 0;
    }

    @Override
    public Optional<ResourcePermissionMatch> findBestMatch(UUID workspaceId, UUID userId, String resourceType, UUID resourceId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    with candidate_permissions as (
                        select rp.*, 'user' matched_via, rp.subject_id matched_subject_id,
                               coalesce(u.display_name, u.username) subject_name
                        from resource_permissions rp
                        join users u on u.id = rp.subject_id and u.workspace_id = rp.workspace_id and u.deleted_at is null
                        where rp.workspace_id = ?
                          and rp.resource_type = ?
                          and rp.resource_id = ?
                          and rp.status = 'active'
                          and (rp.expires_at is null or rp.expires_at > now())
                          and rp.subject_type = 'user'
                          and rp.subject_id = ?
                        union all
                        select rp.*, 'department' matched_via, dm.department_id matched_subject_id, d.name subject_name
                        from resource_permissions rp
                        join departments d on d.id = rp.subject_id
                            and d.workspace_id = rp.workspace_id
                            and d.status = 'active'
                            and d.deleted_at is null
                        join department_members dm on dm.workspace_id = rp.workspace_id
                            and dm.department_id = d.id
                            and dm.user_id = ?
                            and dm.ended_at is null
                        where rp.workspace_id = ?
                          and rp.resource_type = ?
                          and rp.resource_id = ?
                          and rp.status = 'active'
                          and (rp.expires_at is null or rp.expires_at > now())
                          and rp.subject_type = 'department'
                        union all
                        select rp.*, 'user_group' matched_via, ug.id matched_subject_id, ug.name subject_name
                        from resource_permissions rp
                        join user_groups ug on ug.id = rp.subject_id
                            and ug.workspace_id = rp.workspace_id
                            and ug.status = 'active'
                            and ug.deleted_at is null
                        where rp.workspace_id = ?
                          and rp.resource_type = ?
                          and rp.resource_id = ?
                          and rp.status = 'active'
                          and (rp.expires_at is null or rp.expires_at > now())
                          and rp.subject_type = 'user_group'
                          and exists (
                              select 1
                              from user_group_members ugm
                              where ugm.workspace_id = rp.workspace_id
                                and ugm.group_id = ug.id
                                and ugm.subject_type = 'user'
                                and ugm.subject_id = ?
                                and ugm.removed_at is null
                              union all
                              select 1
                              from user_group_members ugm
                              join department_members dm on dm.department_id = ugm.subject_id
                                  and dm.user_id = ?
                                  and dm.ended_at is null
                              join departments d on d.id = ugm.subject_id
                                  and d.workspace_id = ugm.workspace_id
                                  and d.status = 'active'
                                  and d.deleted_at is null
                              where ugm.workspace_id = rp.workspace_id
                                and ugm.group_id = ug.id
                                and ugm.subject_type = 'department'
                                and ugm.removed_at is null
                          )
                        union all
                        select rp.*, 'role' matched_via, r.id matched_subject_id, r.name subject_name
                        from resource_permissions rp
                        join roles r on r.id = rp.subject_id and r.workspace_id = rp.workspace_id
                        join user_roles ur on ur.role_id = r.id
                            and ur.workspace_id = rp.workspace_id
                            and ur.user_id = ?
                        where rp.workspace_id = ?
                          and rp.resource_type = ?
                          and rp.resource_id = ?
                          and rp.status = 'active'
                          and (rp.expires_at is null or rp.expires_at > now())
                          and rp.subject_type = 'role'
                    )
                    select id, resource_type, resource_id, subject_type, subject_id, permission_level,
                           source_type, source_id, expires_at, matched_via, matched_subject_id, subject_name,
                           case permission_level
                               when 'owner' then 5
                               when 'manage' then 4
                               when 'edit' then 3
                               when 'comment' then 2
                               when 'view' then 1
                               else 0
                           end permission_rank
                    from candidate_permissions
                    order by permission_rank desc, created_at asc
                    limit 1
                    """,
                this::mapMatch,
                workspaceId,
                resourceType,
                resourceId,
                userId,
                userId,
                workspaceId,
                resourceType,
                resourceId,
                workspaceId,
                resourceType,
                resourceId,
                userId,
                userId,
                userId,
                workspaceId,
                resourceType,
                resourceId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean subjectExists(UUID workspaceId, String subjectType, UUID subjectId) {
        Boolean exists = switch (subjectType) {
            case "user" -> jdbcTemplate.queryForObject(
                "select exists(select 1 from users where workspace_id = ? and id = ? and deleted_at is null)",
                Boolean.class,
                workspaceId,
                subjectId
            );
            case "department" -> jdbcTemplate.queryForObject(
                "select exists(select 1 from departments where workspace_id = ? and id = ? and deleted_at is null and status = 'active')",
                Boolean.class,
                workspaceId,
                subjectId
            );
            case "user_group" -> jdbcTemplate.queryForObject(
                "select exists(select 1 from user_groups where workspace_id = ? and id = ? and deleted_at is null and status = 'active')",
                Boolean.class,
                workspaceId,
                subjectId
            );
            case "role" -> jdbcTemplate.queryForObject(
                "select exists(select 1 from roles where workspace_id = ? and id = ?)",
                Boolean.class,
                workspaceId,
                subjectId
            );
            default -> false;
        };
        return Boolean.TRUE.equals(exists);
    }

    private ResourcePermissionMatch mapMatch(ResultSet rs, int rowNum) throws SQLException {
        return new ResourcePermissionMatch(
            rs.getObject("id", UUID.class),
            rs.getString("resource_type"),
            rs.getObject("resource_id", UUID.class),
            rs.getString("subject_type"),
            rs.getObject("subject_id", UUID.class),
            rs.getString("permission_level"),
            rs.getString("source_type"),
            rs.getObject("source_id", UUID.class),
            timestampToInstant(rs, "expires_at"),
            rs.getString("matched_via"),
            rs.getObject("matched_subject_id", UUID.class),
            rs.getString("subject_name")
        );
    }

    private ResourcePermissionEntry mapEntry(ResultSet rs, int rowNum) throws SQLException {
        Instant expiresAt = timestampToInstant(rs, "expires_at");
        return new ResourcePermissionEntry(
            rs.getObject("id", UUID.class),
            rs.getString("resource_type"),
            rs.getObject("resource_id", UUID.class),
            rs.getString("subject_type"),
            rs.getObject("subject_id", UUID.class),
            rs.getString("subject_name"),
            rs.getString("subject_detail"),
            rs.getString("permission_level"),
            rs.getString("source_type"),
            rs.getObject("source_id", UUID.class),
            expiresAt,
            rs.getString("status"),
            rs.getString("effective_status"),
            timestampToInstant(rs, "created_at"),
            timestampToInstant(rs, "updated_at"),
            rs.getLong("expanded_member_count")
        );
    }

    private Instant timestampToInstant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toInstant();
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
