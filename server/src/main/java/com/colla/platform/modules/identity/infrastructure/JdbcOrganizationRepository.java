package com.colla.platform.modules.identity.infrastructure;

import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentManager;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentMember;
import com.colla.platform.modules.identity.domain.OrganizationModels.DepartmentSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrganizationRepository implements OrganizationRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcOrganizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DepartmentSummary> listDepartments(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select d.id, d.parent_id, d.code, d.name, d.path, d.depth, d.sort_order, d.status,
                       d.created_at, d.updated_at,
                       coalesce((
                           select count(*)
                           from department_members dm
                           where dm.workspace_id = d.workspace_id
                             and dm.department_id = d.id
                             and dm.ended_at is null
                       ), 0) member_count,
                       coalesce((
                           select count(*)
                           from department_managers mg
                           where mg.workspace_id = d.workspace_id
                             and mg.department_id = d.id
                       ), 0) manager_count
                from departments d
                where d.workspace_id = ? and d.deleted_at is null
                order by d.depth, d.sort_order, d.name
                """,
            this::mapDepartment,
            workspaceId
        );
    }

    @Override
    public Optional<DepartmentSummary> findDepartment(UUID workspaceId, UUID departmentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select d.id, d.parent_id, d.code, d.name, d.path, d.depth, d.sort_order, d.status,
                           d.created_at, d.updated_at,
                           coalesce((
                               select count(*)
                               from department_members dm
                               where dm.workspace_id = d.workspace_id
                                 and dm.department_id = d.id
                                 and dm.ended_at is null
                           ), 0) member_count,
                           coalesce((
                               select count(*)
                               from department_managers mg
                               where mg.workspace_id = d.workspace_id
                                 and mg.department_id = d.id
                           ), 0) manager_count
                    from departments d
                    where d.workspace_id = ? and d.id = ? and d.deleted_at is null
                    """,
                this::mapDepartment,
                workspaceId,
                departmentId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<DepartmentManager> listDepartmentManagers(UUID workspaceId, UUID departmentId) {
        return jdbcTemplate.query(
            """
                select mg.id, mg.department_id, mg.user_id, u.username, u.display_name, mg.manager_type, mg.created_at
                from department_managers mg
                join users u on u.id = mg.user_id
                where mg.workspace_id = ?
                  and mg.department_id = ?
                  and u.deleted_at is null
                order by case mg.manager_type when 'primary' then 0 else 1 end, mg.created_at
                """,
            this::mapManager,
            workspaceId,
            departmentId
        );
    }

    @Override
    public List<DepartmentMember> listDepartmentMembers(UUID workspaceId, UUID departmentId) {
        return jdbcTemplate.query(
            """
                select dm.id, dm.department_id, dm.user_id, u.username, u.display_name, u.email,
                       dm.relation_type, u.status, dm.started_at, dm.ended_at
                from department_members dm
                join users u on u.id = dm.user_id
                where dm.workspace_id = ?
                  and dm.department_id = ?
                  and dm.ended_at is null
                  and u.deleted_at is null
                order by case dm.relation_type when 'primary' then 0 else 1 end, u.display_name, u.username
                """,
            this::mapMember,
            workspaceId,
            departmentId
        );
    }

    @Override
    public UUID createDepartment(UUID workspaceId, UUID parentId, String code, String name, int sortOrder, UUID actorId) {
        UUID id = UUID.randomUUID();
        ParentPath parent = parentId == null ? new ParentPath("", -1) : findParentPath(workspaceId, parentId).orElseThrow();
        String path = parent.path().isBlank() ? "/" + id : parent.path() + "/" + id;
        int depth = parent.depth() + 1;
        jdbcTemplate.update(
            """
                insert into departments
                    (id, workspace_id, parent_id, code, name, path, depth, sort_order, status, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'active', ?, now(), ?, now())
                """,
            id,
            workspaceId,
            parentId,
            code,
            name,
            path,
            depth,
            sortOrder,
            actorId,
            actorId
        );
        return id;
    }

    @Override
    public void updateDepartment(UUID workspaceId, UUID departmentId, String code, String name, int sortOrder, UUID actorId) {
        jdbcTemplate.update(
            """
                update departments
                set code = ?, name = ?, sort_order = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            code,
            name,
            sortOrder,
            actorId,
            workspaceId,
            departmentId
        );
    }

    @Override
    public void moveDepartment(UUID workspaceId, UUID departmentId, UUID parentId, int sortOrder, UUID actorId) {
        DepartmentSummary current = findDepartment(workspaceId, departmentId).orElseThrow();
        ParentPath parent = parentId == null ? new ParentPath("", -1) : findParentPath(workspaceId, parentId).orElseThrow();
        String newPath = parent.path().isBlank() ? "/" + departmentId : parent.path() + "/" + departmentId;
        int newDepth = parent.depth() + 1;
        jdbcTemplate.update(
            """
                update departments
                set parent_id = ?, path = ?, depth = ?, sort_order = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            parentId,
            newPath,
            newDepth,
            sortOrder,
            actorId,
            workspaceId,
            departmentId
        );
        updateDescendantPaths(workspaceId, departmentId, current.path(), newPath, newDepth);
    }

    @Override
    public void disableDepartment(UUID workspaceId, UUID departmentId, UUID actorId) {
        jdbcTemplate.update(
            """
                update departments
                set status = 'disabled', updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            actorId,
            workspaceId,
            departmentId
        );
    }

    @Override
    public void deleteDepartment(UUID workspaceId, UUID departmentId) {
        jdbcTemplate.update(
            """
                update departments
                set deleted_at = now(), updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            workspaceId,
            departmentId
        );
    }

    @Override
    public boolean hasChildren(UUID workspaceId, UUID departmentId) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from departments where workspace_id = ? and parent_id = ? and deleted_at is null",
            Integer.class,
            workspaceId,
            departmentId
        );
        return count != null && count > 0;
    }

    @Override
    public boolean hasActiveMembers(UUID workspaceId, UUID departmentId) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from department_members where workspace_id = ? and department_id = ? and ended_at is null",
            Integer.class,
            workspaceId,
            departmentId
        );
        return count != null && count > 0;
    }

    @Override
    public void addDepartmentMember(UUID workspaceId, UUID departmentId, UUID userId, String relationType, UUID actorId) {
        if ("primary".equals(relationType)) {
            jdbcTemplate.update(
                """
                    update department_members
                    set ended_at = now()
                    where workspace_id = ?
                      and user_id = ?
                      and relation_type = 'primary'
                      and ended_at is null
                      and department_id <> ?
                    """,
                workspaceId,
                userId,
                departmentId
            );
        }
        Integer existing = jdbcTemplate.queryForObject(
            """
                select count(*)
                from department_members
                where workspace_id = ?
                  and department_id = ?
                  and user_id = ?
                  and relation_type = ?
                  and ended_at is null
                """,
            Integer.class,
            workspaceId,
            departmentId,
            userId,
            relationType
        );
        if (existing == null || existing == 0) {
            jdbcTemplate.update(
                """
                    insert into department_members
                        (id, workspace_id, department_id, user_id, relation_type, started_at, created_by, created_at)
                    values (?, ?, ?, ?, ?, now(), ?, now())
                    """,
                UUID.randomUUID(),
                workspaceId,
                departmentId,
                userId,
                relationType,
                actorId
            );
        }
    }

    @Override
    public void removeDepartmentMember(UUID workspaceId, UUID departmentId, UUID userId) {
        jdbcTemplate.update(
            """
                update department_members
                set ended_at = now()
                where workspace_id = ?
                  and department_id = ?
                  and user_id = ?
                  and ended_at is null
                """,
            workspaceId,
            departmentId,
            userId
        );
        jdbcTemplate.update(
            """
                delete from department_managers
                where workspace_id = ?
                  and department_id = ?
                  and user_id = ?
                """,
            workspaceId,
            departmentId,
            userId
        );
    }

    @Override
    public void addDepartmentManager(UUID workspaceId, UUID departmentId, UUID userId, String managerType, UUID actorId) {
        jdbcTemplate.update(
            """
                insert into department_managers
                    (id, workspace_id, department_id, user_id, manager_type, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, now())
                on conflict (workspace_id, department_id, user_id, manager_type) do nothing
                """,
            UUID.randomUUID(),
            workspaceId,
            departmentId,
            userId,
            managerType,
            actorId
        );
    }

    @Override
    public void removeDepartmentManager(UUID workspaceId, UUID departmentId, UUID userId, String managerType) {
        jdbcTemplate.update(
            """
                delete from department_managers
                where workspace_id = ?
                  and department_id = ?
                  and user_id = ?
                  and manager_type = ?
                """,
            workspaceId,
            departmentId,
            userId,
            managerType
        );
    }

    private void updateDescendantPaths(UUID workspaceId, UUID departmentId, String oldPath, String newPath, int rootDepth) {
        List<DepartmentPath> descendants = jdbcTemplate.query(
            """
                select id, path
                from departments
                where workspace_id = ?
                  and path like ?
                  and id <> ?
                  and deleted_at is null
                order by depth
                """,
            (rs, rowNum) -> new DepartmentPath(rs.getObject("id", UUID.class), rs.getString("path")),
            workspaceId,
            oldPath + "/%",
            departmentId
        );
        for (DepartmentPath descendant : descendants) {
            String descendantPath = newPath + descendant.path().substring(oldPath.length());
            int depth = rootDepth + descendantPath.substring(newPath.length()).split("/", -1).length - 1;
            jdbcTemplate.update(
                "update departments set path = ?, depth = ?, updated_at = now() where workspace_id = ? and id = ?",
                descendantPath,
                depth,
                workspaceId,
                descendant.id()
            );
        }
    }

    private Optional<ParentPath> findParentPath(UUID workspaceId, UUID departmentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select path, depth
                    from departments
                    where workspace_id = ? and id = ? and deleted_at is null
                    """,
                (rs, rowNum) -> new ParentPath(rs.getString("path"), rs.getInt("depth")),
                workspaceId,
                departmentId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private DepartmentSummary mapDepartment(ResultSet rs, int rowNum) throws SQLException {
        return new DepartmentSummary(
            rs.getObject("id", UUID.class),
            rs.getObject("parent_id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("path"),
            rs.getInt("depth"),
            rs.getInt("sort_order"),
            rs.getString("status"),
            rs.getInt("member_count"),
            rs.getInt("manager_count"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private DepartmentMember mapMember(ResultSet rs, int rowNum) throws SQLException {
        return new DepartmentMember(
            rs.getObject("id", UUID.class),
            rs.getObject("department_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("email"),
            rs.getString("relation_type"),
            rs.getString("status"),
            rs.getTimestamp("started_at").toInstant(),
            rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toInstant()
        );
    }

    private DepartmentManager mapManager(ResultSet rs, int rowNum) throws SQLException {
        return new DepartmentManager(
            rs.getObject("id", UUID.class),
            rs.getObject("department_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("manager_type"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private record ParentPath(String path, int depth) {
    }

    private record DepartmentPath(UUID id, String path) {
    }
}
