package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.ActiveSpaceMap;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.LegacyMemberRow;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.LegacyProjectRow;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.SourceFingerprintRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProjectLegacyMappingRepository implements ProjectLegacyMappingRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcProjectLegacyMappingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<LegacyProjectRow> findActiveProjects(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select p.id project_id, p.project_key, p.name,
                       case when w.id is null then false else true end workspace_exists
                  from projects p
                  left join workspaces w on w.id = p.workspace_id
                 where p.workspace_id = ?
                   and p.archived_at is null
                 order by p.id
                """,
            (resultSet, rowNumber) -> new LegacyProjectRow(
                resultSet.getObject("project_id", UUID.class),
                resultSet.getString("project_key"),
                resultSet.getString("name"),
                resultSet.getBoolean("workspace_exists")
            ),
            workspaceId
        );
    }

    @Override
    public List<LegacyMemberRow> findActiveMembers(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select pm.project_id, pm.user_id, pm.project_role,
                       case
                           when u.id is not null and u.deleted_at is null and u.status <> 'disabled'
                                and u.workspace_id = pm.workspace_id then true
                           else false
                       end user_healthy
                  from project_members pm
                  join projects p on p.id = pm.project_id and p.archived_at is null
                  left join users u on u.id = pm.user_id
                 where pm.workspace_id = ?
                   and pm.archived_at is null
                 order by pm.project_id, pm.user_id
                """,
            (resultSet, rowNumber) -> new LegacyMemberRow(
                resultSet.getObject("project_id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("project_role"),
                resultSet.getBoolean("user_healthy")
            ),
            workspaceId
        );
    }

    @Override
    public List<ActiveSpaceMap> findActiveSpaceMaps(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select legacy_project_id, space_id
                  from project_legacy_space_maps
                 where workspace_id = ?
                   and mapping_status = 'active'
                 order by legacy_project_id
                """,
            (resultSet, rowNumber) -> new ActiveSpaceMap(
                resultSet.getObject("legacy_project_id", UUID.class),
                resultSet.getObject("space_id", UUID.class)
            ),
            workspaceId
        );
    }

    @Override
    public List<String> findOccupiedSpaceKeys(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select space_key
                  from project_spaces
                 where workspace_id = ?
                 order by space_key
                """,
            (resultSet, rowNumber) -> resultSet.getString("space_key"),
            workspaceId
        );
    }

    @Override
    public List<SourceFingerprintRow> findSourceFingerprintRows(UUID workspaceId) {
        return jdbcTemplate.query(
            """
                select p.id project_id, p.updated_at,
                       count(pm.id) active_members,
                       coalesce(
                           string_agg(pm.user_id::text || ':' || pm.project_role || ':' ||
                                      coalesce(u.status, '<missing>') || ':' ||
                                      (u.id is not null and u.deleted_at is null and u.workspace_id = p.workspace_id), ','
                                      order by pm.user_id::text, pm.project_role),
                           ''
                       ) member_digest
                  from projects p
                  left join project_members pm on pm.project_id = p.id and pm.archived_at is null
                  left join users u on u.id = pm.user_id
                 where p.workspace_id = ?
                   and p.archived_at is null
                 group by p.id, p.updated_at
                 order by p.id
                """,
            (resultSet, rowNumber) -> new SourceFingerprintRow(
                resultSet.getObject("project_id", UUID.class),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("active_members"),
                resultSet.getString("member_digest")
            ),
            workspaceId
        );
    }

    @Override
    public Optional<SourceFingerprintRow> findSourceFingerprintRow(UUID workspaceId, UUID projectId) {
        List<SourceFingerprintRow> rows = jdbcTemplate.query(
            """
                select p.id project_id, p.updated_at,
                       count(pm.id) active_members,
                       coalesce(
                           string_agg(pm.user_id::text || ':' || pm.project_role || ':' ||
                                      coalesce(u.status, '<missing>') || ':' ||
                                      (u.id is not null and u.deleted_at is null and u.workspace_id = p.workspace_id), ','
                                      order by pm.user_id::text, pm.project_role),
                           ''
                       ) member_digest
                  from projects p
                  left join project_members pm on pm.project_id = p.id and pm.archived_at is null
                  left join users u on u.id = pm.user_id
                 where p.workspace_id = ?
                   and p.id = ?
                   and p.archived_at is null
                 group by p.id, p.updated_at
                """,
            (resultSet, rowNumber) -> new SourceFingerprintRow(
                resultSet.getObject("project_id", UUID.class),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("active_members"),
                resultSet.getString("member_digest")
            ),
            workspaceId,
            projectId
        );
        return rows.stream().findFirst();
    }

    @Override
    public Instant findSourceWatermark(UUID workspaceId) {
        Timestamp watermark = jdbcTemplate.queryForObject(
            """
                select max(stamp) from (
                    select updated_at stamp from projects
                     where workspace_id = ? and archived_at is null
                    union all
                    select joined_at stamp from project_members
                     where workspace_id = ? and archived_at is null
                    union all
                    select u.updated_at stamp from users u
                      join project_members pm on pm.user_id = u.id and pm.archived_at is null
                      join projects p on p.id = pm.project_id and p.archived_at is null
                     where p.workspace_id = ?
                ) source_stamps
                """,
            Timestamp.class,
            workspaceId,
            workspaceId,
            workspaceId
        );
        return watermark == null ? null : watermark.toInstant();
    }
}
