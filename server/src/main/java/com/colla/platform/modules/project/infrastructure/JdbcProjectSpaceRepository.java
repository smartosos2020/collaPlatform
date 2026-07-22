package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
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
public class JdbcProjectSpaceRepository implements ProjectSpaceRepository {
    private static final String SUMMARY_SELECT = """
        select s.id, s.workspace_id, s.space_key, s.name, s.description, s.status, s.visibility,
               s.version, s.created_by, s.created_at, s.updated_by, s.updated_at, s.disabled_at, s.archived_at,
               (select pra.role_key
                  from project_space_members psm
                  join project_space_role_assignments pra on pra.member_id = psm.id and pra.revoked_at is null
                 where psm.workspace_id = s.workspace_id and psm.space_id = s.id and psm.user_id = ?
                   and psm.status = 'active'
                 limit 1) current_user_role,
               (select count(*) from project_space_members active_members
                 where active_members.space_id = s.id and active_members.status = 'active') member_count
          from project_spaces s
        """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcProjectSpaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UUID createSpaceWithOwner(
        UUID workspaceId,
        String spaceKey,
        String name,
        String description,
        String visibility,
        UUID ownerId
    ) {
        UUID spaceId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_spaces
                    (id, workspace_id, space_key, name, description, status, visibility,
                     created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, 'active', ?, ?, now(), ?, now())
                """,
            spaceId,
            workspaceId,
            spaceKey,
            name,
            description,
            visibility,
            ownerId,
            ownerId
        );
        jdbcTemplate.update(
            """
                insert into project_space_members
                    (id, workspace_id, space_id, user_id, status, joined_at,
                     created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, 'active', now(), ?, now(), ?, now())
                """,
            memberId,
            workspaceId,
            spaceId,
            ownerId,
            ownerId,
            ownerId
        );
        jdbcTemplate.update(
            """
                insert into project_space_role_assignments
                    (id, workspace_id, space_id, member_id, role_key, assigned_by, assigned_at)
                values (?, ?, ?, ?, 'owner', ?, now())
                """,
            UUID.randomUUID(),
            workspaceId,
            spaceId,
            memberId,
            ownerId
        );
        return spaceId;
    }

    @Override
    public void createMigratedSpace(
        UUID workspaceId,
        UUID spaceId,
        String spaceKey,
        String name,
        String description,
        String visibility,
        UUID actorId
    ) {
        // Migration-only counterpart of createSpaceWithOwner: the caller supplies the deterministic
        // space id and writes members separately, so this inserts only the space row with the same
        // column shape as the regular creation path.
        jdbcTemplate.update(
            """
                insert into project_spaces
                    (id, workspace_id, space_key, name, description, status, visibility,
                     created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, 'active', ?, ?, now(), ?, now())
                """,
            spaceId,
            workspaceId,
            spaceKey,
            name,
            description,
            visibility,
            actorId,
            actorId
        );
    }

    @Override
    public void deleteSpace(UUID workspaceId, UUID spaceId) {
        jdbcTemplate.execute("set local colla.project_space_cleanup = 'on'");
        jdbcTemplate.update(
            "delete from project_work_item_type_versions where workspace_id = ? and space_id = ?",
            workspaceId,
            spaceId
        );
        jdbcTemplate.update(
            "delete from project_work_item_types where workspace_id = ? and space_id = ?",
            workspaceId,
            spaceId
        );
        jdbcTemplate.update(
            "delete from project_spaces where workspace_id = ? and id = ?",
            workspaceId,
            spaceId
        );
    }

    @Override
    public List<ProjectSpaceSummary> listVisible(UUID workspaceId, UUID userId) {
        return jdbcTemplate.query(
            SUMMARY_SELECT + """
                 where s.workspace_id = ? and s.status <> 'archived'
                   and (
                       s.visibility in ('discoverable', 'workspace')
                       or exists (
                           select 1 from project_space_members member_scope
                            where member_scope.space_id = s.id and member_scope.user_id = ?
                              and member_scope.status = 'active'
                       )
                   )
                 order by s.updated_at desc, s.id
                """,
            this::mapSummary,
            userId,
            workspaceId,
            userId
        );
    }

    @Override
    public List<ProjectSpaceSummary> listGovernance(
        UUID workspaceId,
        UUID userId,
        String status,
        String visibility,
        boolean includeArchived,
        int limit,
        int offset
    ) {
        return jdbcTemplate.query(
            SUMMARY_SELECT + """
                 where s.workspace_id = ?
                   and (? = '' or s.status = ?)
                   and (? = '' or s.visibility = ?)
                   and (? or s.status <> 'archived')
                 order by s.updated_at desc, s.id
                 limit ? offset ?
                """,
            this::mapSummary,
            userId,
            workspaceId,
            status,
            status,
            visibility,
            visibility,
            includeArchived,
            limit,
            offset
        );
    }

    @Override
    public Optional<ProjectSpaceSummary> findById(UUID workspaceId, UUID spaceId, UUID userId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                SUMMARY_SELECT + " where s.workspace_id = ? and s.id = ?",
                this::mapSummary,
                userId,
                workspaceId,
                spaceId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> findActiveRole(UUID workspaceId, UUID spaceId, UUID userId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select pra.role_key
                      from project_space_members psm
                      join project_space_role_assignments pra on pra.member_id = psm.id and pra.revoked_at is null
                     where psm.workspace_id = ? and psm.space_id = ? and psm.user_id = ? and psm.status = 'active'
                    """,
                String.class,
                workspaceId,
                spaceId,
                userId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void updateSpace(UUID workspaceId, UUID spaceId, String name, String description, String visibility, UUID actorId) {
        jdbcTemplate.update(
            """
                update project_spaces
                   set name = ?, description = ?, visibility = ?, updated_by = ?, updated_at = now(), version = version + 1
                 where workspace_id = ? and id = ? and status <> 'archived'
                """,
            name,
            description,
            visibility,
            actorId,
            workspaceId,
            spaceId
        );
    }

    @Override
    public void transitionSpace(UUID workspaceId, UUID spaceId, String targetStatus, UUID actorId) {
        jdbcTemplate.update(
            """
                update project_spaces
                   set status = ?,
                       disabled_at = case when ? = 'disabled' then coalesce(disabled_at, now()) else null end,
                       archived_at = case when ? = 'archived' then coalesce(archived_at, now()) else null end,
                       updated_by = ?, updated_at = now(), version = version + 1
                 where workspace_id = ? and id = ?
                """,
            targetStatus,
            targetStatus,
            targetStatus,
            actorId,
            workspaceId,
            spaceId
        );
    }

    private ProjectSpaceSummary mapSummary(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProjectSpaceSummary(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            resultSet.getString("space_key"),
            resultSet.getString("name"),
            resultSet.getString("description"),
            resultSet.getString("status"),
            resultSet.getString("visibility"),
            resultSet.getLong("version"),
            resultSet.getString("current_user_role"),
            resultSet.getInt("member_count"),
            resultSet.getObject("created_by", UUID.class),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getObject("updated_by", UUID.class),
            resultSet.getTimestamp("updated_at").toInstant(),
            instant(resultSet.getTimestamp("disabled_at")),
            instant(resultSet.getTimestamp("archived_at"))
        );
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
