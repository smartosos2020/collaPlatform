package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.LegacySpaceMapRecord;
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
public class JdbcProjectLegacySpaceMapRepository implements ProjectLegacySpaceMapRepository {
    private static final String MAP_SELECT = """
        select id, workspace_id, legacy_project_id, space_id, mapping_version, mapping_status,
               source_checksum, batch_id, mapped_by, mapped_at, rolled_back_by, rolled_back_at
          from project_legacy_space_maps
        """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcProjectLegacySpaceMapRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insertMap(
        UUID workspaceId,
        UUID legacyProjectId,
        UUID spaceId,
        int mappingVersion,
        UUID batchId,
        String sourceChecksum,
        UUID mappedBy
    ) {
        // The (workspace_id, legacy_project_id) unique constraint allows one map row per legacy
        // project, so a project re-migrated after a rollback reactivates its rolled-back row
        // instead of inserting a duplicate.
        jdbcTemplate.update(
            """
                insert into project_legacy_space_maps
                    (id, workspace_id, legacy_project_id, space_id, mapping_version, mapping_status,
                     source_checksum, batch_id, mapped_by, mapped_at)
                values (?, ?, ?, ?, ?, 'active', ?, ?, ?, now())
                on conflict (workspace_id, legacy_project_id)
                do update set space_id = excluded.space_id,
                              mapping_status = 'active',
                              mapping_version = project_legacy_space_maps.mapping_version + 1,
                              source_checksum = excluded.source_checksum,
                              batch_id = excluded.batch_id,
                              mapped_by = excluded.mapped_by,
                              mapped_at = now(),
                              rolled_back_by = null,
                              rolled_back_at = null
                """,
            UUID.randomUUID(),
            workspaceId,
            legacyProjectId,
            spaceId,
            mappingVersion,
            sourceChecksum,
            batchId,
            mappedBy
        );
    }

    @Override
    public Optional<LegacySpaceMapRecord> findActiveByProject(UUID workspaceId, UUID legacyProjectId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                MAP_SELECT + " where workspace_id = ? and legacy_project_id = ? and mapping_status = 'active'",
                this::mapRecord,
                workspaceId,
                legacyProjectId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<LegacySpaceMapRecord> findByProject(UUID workspaceId, UUID legacyProjectId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                MAP_SELECT + " where workspace_id = ? and legacy_project_id = ?",
                this::mapRecord,
                workspaceId,
                legacyProjectId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<LegacySpaceMapRecord> findByBatch(UUID workspaceId, UUID batchId) {
        return jdbcTemplate.query(
            MAP_SELECT + " where workspace_id = ? and batch_id = ? order by legacy_project_id",
            this::mapRecord,
            workspaceId,
            batchId
        );
    }

    @Override
    public List<LegacySpaceMapRecord> findActiveByWorkspace(UUID workspaceId) {
        return jdbcTemplate.query(
            MAP_SELECT + " where workspace_id = ? and mapping_status = 'active' order by legacy_project_id",
            this::mapRecord,
            workspaceId
        );
    }

    @Override
    public void markRolledBack(UUID workspaceId, UUID mapId, UUID actorId) {
        // space_id is cleared so the rollback unit can delete the space row without violating the
        // map -> space foreign key while the rolled-back map row is preserved for audit.
        jdbcTemplate.update(
            """
                update project_legacy_space_maps
                   set mapping_status = 'rolled_back', space_id = null,
                       rolled_back_by = ?, rolled_back_at = now()
                 where workspace_id = ? and id = ? and mapping_status = 'active'
                """,
            actorId,
            workspaceId,
            mapId
        );
    }

    private LegacySpaceMapRecord mapRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LegacySpaceMapRecord(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            resultSet.getObject("legacy_project_id", UUID.class),
            resultSet.getObject("space_id", UUID.class),
            resultSet.getInt("mapping_version"),
            resultSet.getString("mapping_status"),
            resultSet.getString("source_checksum"),
            resultSet.getObject("batch_id", UUID.class),
            resultSet.getObject("mapped_by", UUID.class),
            resultSet.getTimestamp("mapped_at").toInstant(),
            resultSet.getObject("rolled_back_by", UUID.class),
            instant(resultSet.getTimestamp("rolled_back_at"))
        );
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
