package com.colla.platform.modules.permission.infrastructure;

import com.colla.platform.modules.permission.domain.PermissionModels.PermissionCatalogItem;
import com.colla.platform.modules.permission.domain.PermissionModels.RoleAssignmentSummary;
import com.colla.platform.modules.permission.domain.PermissionModels.RoleDetail;
import com.colla.platform.modules.permission.domain.PermissionModels.RoleSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRoleRepository implements RoleRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcRoleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<PermissionCatalogItem> listPermissions() {
        return jdbcTemplate.query(
            """
                select id, code, name, module, description, risk_level, is_builtin, display_order
                from permissions
                order by display_order, module, code
                """,
            this::mapPermission
        );
    }

    @Override
    public List<PermissionCatalogItem> listPermissionsByCodes(List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(permissionCodes.size(), "?"));
        return jdbcTemplate.query(
            """
                select id, code, name, module, description, risk_level, is_builtin, display_order
                from permissions
                where code in (%s)
                order by display_order, module, code
                """.formatted(placeholders),
            this::mapPermission,
            permissionCodes.toArray()
        );
    }

    @Override
    public List<RoleSummary> listRoles(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select id, code, name, scope, description, status, is_builtin, created_at, updated_at
                from roles
                where workspace_id = ?
                order by is_builtin desc, lower(name), created_at
                """,
            (rs, rowNum) -> mapRoleSummary(rs, findPermissionCodes(rs.getObject("id", UUID.class))),
            workspaceId
        );
    }

    @Override
    public Optional<RoleDetail> findRole(UUID workspaceId, UUID roleId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, code, name, scope, description, status, is_builtin, created_at, updated_at
                    from roles
                    where workspace_id = ? and id = ?
                    """,
                (rs, rowNum) -> mapRoleDetail(rs, listRolePermissions(roleId), listAssignments(workspaceId, roleId)),
                workspaceId,
                roleId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<RoleSummary> findRoleSummary(UUID workspaceId, UUID roleId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, code, name, scope, description, status, is_builtin, created_at, updated_at
                    from roles
                    where workspace_id = ? and id = ?
                    """,
                (rs, rowNum) -> mapRoleSummary(rs, findPermissionCodes(roleId)),
                workspaceId,
                roleId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public RoleSummary createRole(UUID workspaceId, String code, String name, String scope, String description, UUID actorId) {
        UUID roleId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into roles
                    (id, workspace_id, code, name, scope, is_builtin, description, status, created_at, updated_at, updated_by)
                values (?, ?, ?, ?, ?, false, ?, 'active', now(), now(), ?)
                """,
            roleId,
            workspaceId,
            code,
            name,
            scope,
            description,
            actorId
        );
        return findRoleSummary(workspaceId, roleId).orElseThrow();
    }

    @Override
    public RoleSummary updateRole(
        UUID workspaceId,
        UUID roleId,
        String name,
        String scope,
        String description,
        String status,
        UUID actorId
    ) {
        jdbcTemplate.update(
            """
                update roles
                set name = ?, scope = ?, description = ?, status = ?, updated_at = now(), updated_by = ?
                where workspace_id = ? and id = ?
                """,
            name,
            scope,
            description,
            status,
            actorId,
            workspaceId,
            roleId
        );
        return findRoleSummary(workspaceId, roleId).orElseThrow();
    }

    @Override
    public void replaceRolePermissions(UUID workspaceId, UUID roleId, List<String> permissionCodes) {
        jdbcTemplate.update(
            "delete from role_permissions where role_id = ?",
            roleId
        );
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }
        for (String permissionCode : permissionCodes) {
            jdbcTemplate.update(
                """
                    insert into role_permissions (role_id, permission_id)
                    select ?, p.id
                    from permissions p
                    where p.code = ?
                    on conflict do nothing
                    """,
                roleId,
                permissionCode
            );
        }
        jdbcTemplate.update(
            "update roles set updated_at = now() where workspace_id = ? and id = ?",
            workspaceId,
            roleId
        );
    }

    @Override
    public List<RoleAssignmentSummary> listAssignments(UUID workspaceId, UUID roleId) {
        List<Object> args = new ArrayList<>();
        args.add(workspaceId);
        String roleFilter = "";
        if (roleId != null) {
            roleFilter = " and ra.role_id = ?";
            args.add(roleId);
        }
        return jdbcTemplate.query(
            """
                select ra.id, ra.role_id, r.code role_code, r.name role_name, ra.subject_type, ra.subject_id,
                       coalesce(u.display_name, d.name, ug.name) subject_name,
                       coalesce(u.username, d.code, ug.code) subject_detail,
                       ra.scope_type, ra.scope_id, ra.effective_at, ra.expires_at, ra.status, ra.created_at
                from role_assignments ra
                join roles r on r.id = ra.role_id
                left join users u on ra.subject_type = 'user' and u.id = ra.subject_id and u.deleted_at is null
                left join departments d on ra.subject_type = 'department' and d.id = ra.subject_id and d.deleted_at is null
                left join user_groups ug on ra.subject_type = 'user_group' and ug.id = ra.subject_id and ug.deleted_at is null
                where ra.workspace_id = ?
                %s
                order by ra.status, ra.created_at desc
                """.formatted(roleFilter),
            this::mapAssignment,
            args.toArray()
        );
    }

    @Override
    public RoleAssignmentSummary createAssignment(
        UUID workspaceId,
        UUID roleId,
        String subjectType,
        UUID subjectId,
        String scopeType,
        UUID scopeId,
        Instant effectiveAt,
        Instant expiresAt,
        UUID actorId
    ) {
        UUID assignmentId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into role_assignments
                    (id, workspace_id, role_id, subject_type, subject_id, scope_type, scope_id,
                     effective_at, expires_at, status, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, ?, coalesce(?, now()), ?, 'active', ?, now())
                """,
            assignmentId,
            workspaceId,
            roleId,
            subjectType,
            subjectId,
            scopeType,
            scopeId,
            effectiveAt == null ? null : Timestamp.from(effectiveAt),
            expiresAt == null ? null : Timestamp.from(expiresAt),
            actorId
        );
        return findAssignment(workspaceId, assignmentId).orElseThrow();
    }

    @Override
    public boolean revokeAssignment(UUID workspaceId, UUID assignmentId, UUID actorId) {
        return jdbcTemplate.update(
            """
                update role_assignments
                set status = 'revoked', revoked_by = ?, revoked_at = now()
                where workspace_id = ? and id = ? and status = 'active'
                """,
            actorId,
            workspaceId,
            assignmentId
        ) > 0;
    }

    @Override
    public boolean subjectExists(UUID workspaceId, String subjectType, UUID subjectId) {
        String sql = switch (subjectType) {
            case "user" -> "select count(*) from users where workspace_id = ? and id = ? and deleted_at is null and status = 'active'";
            case "department" -> "select count(*) from departments where workspace_id = ? and id = ? and deleted_at is null and status = 'active'";
            case "user_group" -> "select count(*) from user_groups where workspace_id = ? and id = ? and deleted_at is null and status = 'active'";
            default -> null;
        };
        if (sql == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, workspaceId, subjectId);
        return count != null && count > 0;
    }

    private Optional<RoleAssignmentSummary> findAssignment(UUID workspaceId, UUID assignmentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select ra.id, ra.role_id, r.code role_code, r.name role_name, ra.subject_type, ra.subject_id,
                           coalesce(u.display_name, d.name, ug.name) subject_name,
                           coalesce(u.username, d.code, ug.code) subject_detail,
                           ra.scope_type, ra.scope_id, ra.effective_at, ra.expires_at, ra.status, ra.created_at
                    from role_assignments ra
                    join roles r on r.id = ra.role_id
                    left join users u on ra.subject_type = 'user' and u.id = ra.subject_id and u.deleted_at is null
                    left join departments d on ra.subject_type = 'department' and d.id = ra.subject_id and d.deleted_at is null
                    left join user_groups ug on ra.subject_type = 'user_group' and ug.id = ra.subject_id and ug.deleted_at is null
                    where ra.workspace_id = ? and ra.id = ?
                    """,
                this::mapAssignment,
                workspaceId,
                assignmentId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private List<PermissionCatalogItem> listRolePermissions(UUID roleId) {
        return jdbcTemplate.query(
            """
                select p.id, p.code, p.name, p.module, p.description, p.risk_level, p.is_builtin, p.display_order
                from role_permissions rp
                join permissions p on p.id = rp.permission_id
                where rp.role_id = ?
                order by p.display_order, p.module, p.code
                """,
            this::mapPermission,
            roleId
        );
    }

    private List<String> findPermissionCodes(UUID roleId) {
        return jdbcTemplate.queryForList(
            """
                select p.code
                from role_permissions rp
                join permissions p on p.id = rp.permission_id
                where rp.role_id = ?
                order by p.display_order, p.module, p.code
                """,
            String.class,
            roleId
        );
    }

    private PermissionCatalogItem mapPermission(ResultSet rs, int rowNum) throws SQLException {
        return new PermissionCatalogItem(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("module"),
            rs.getString("description"),
            rs.getString("risk_level"),
            rs.getBoolean("is_builtin"),
            rs.getInt("display_order")
        );
    }

    private RoleSummary mapRoleSummary(ResultSet rs, List<String> permissionCodes) throws SQLException {
        return new RoleSummary(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("scope"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getBoolean("is_builtin"),
            permissionCodes,
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private RoleDetail mapRoleDetail(
        ResultSet rs,
        List<PermissionCatalogItem> permissions,
        List<RoleAssignmentSummary> assignments
    ) throws SQLException {
        return new RoleDetail(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("scope"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getBoolean("is_builtin"),
            permissions,
            assignments,
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private RoleAssignmentSummary mapAssignment(ResultSet rs, int rowNum) throws SQLException {
        Timestamp expiresAt = rs.getTimestamp("expires_at");
        return new RoleAssignmentSummary(
            rs.getObject("id", UUID.class),
            rs.getObject("role_id", UUID.class),
            rs.getString("role_code"),
            rs.getString("role_name"),
            rs.getString("subject_type"),
            rs.getObject("subject_id", UUID.class),
            rs.getString("subject_name"),
            rs.getString("subject_detail"),
            rs.getString("scope_type"),
            rs.getObject("scope_id", UUID.class),
            rs.getTimestamp("effective_at").toInstant(),
            expiresAt == null ? null : expiresAt.toInstant(),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant()
        );
    }
}
