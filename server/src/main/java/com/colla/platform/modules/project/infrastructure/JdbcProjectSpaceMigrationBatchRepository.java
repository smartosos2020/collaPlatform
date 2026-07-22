package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MigrationBatchRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProjectSpaceMigrationBatchRepository implements ProjectSpaceMigrationBatchRepository {
    private static final String BATCH_SELECT = """
        select id, workspace_id, status, dry_run, source_watermark, source_checksum, result_checksum,
               summary, failures, started_by, started_at, finished_at, rolled_back_by, rolled_back_at
          from project_space_migration_batches
        """;
    private static final TypeReference<Map<String, Object>> SUMMARY_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> FAILURES_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcProjectSpaceMigrationBatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UUID insertBatch(
        UUID workspaceId,
        boolean dryRun,
        Instant sourceWatermark,
        String sourceChecksum,
        UUID startedBy
    ) {
        UUID batchId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_space_migration_batches
                    (id, workspace_id, status, dry_run, source_watermark, source_checksum,
                     summary, failures, started_by, started_at)
                values (?, ?, 'running', ?, ?, ?, '{}'::jsonb, '[]'::jsonb, ?, now())
                """,
            batchId,
            workspaceId,
            dryRun,
            sourceWatermark == null ? null : Timestamp.from(sourceWatermark),
            sourceChecksum,
            startedBy
        );
        return batchId;
    }

    @Override
    public Optional<MigrationBatchRecord> findBatch(UUID workspaceId, UUID batchId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                BATCH_SELECT + " where workspace_id = ? and id = ?",
                this::mapBatch,
                workspaceId,
                batchId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<MigrationBatchRecord> listByWorkspace(UUID workspaceId) {
        return jdbcTemplate.query(
            BATCH_SELECT + " where workspace_id = ? order by started_at desc, id",
            this::mapBatch,
            workspaceId
        );
    }

    @Override
    public void updateSourceFingerprint(UUID workspaceId, UUID batchId, Instant sourceWatermark, String sourceChecksum) {
        jdbcTemplate.update(
            """
                update project_space_migration_batches
                   set source_watermark = ?, source_checksum = ?
                 where workspace_id = ? and id = ?
                """,
            sourceWatermark == null ? null : Timestamp.from(sourceWatermark),
            sourceChecksum,
            workspaceId,
            batchId
        );
    }

    @Override
    public void finalizeBatch(
        UUID workspaceId,
        UUID batchId,
        String status,
        String resultChecksum,
        Map<String, Object> summary,
        List<Map<String, Object>> failures
    ) {
        jdbcTemplate.update(
            """
                update project_space_migration_batches
                   set status = ?, result_checksum = ?, summary = ?::jsonb, failures = ?::jsonb,
                       finished_at = now()
                 where workspace_id = ? and id = ?
                """,
            status,
            resultChecksum,
            toJson(summary),
            toJson(failures),
            workspaceId,
            batchId
        );
    }

    @Override
    public void markBatchRolledBack(
        UUID workspaceId,
        UUID batchId,
        String status,
        UUID actorId,
        Map<String, Object> summary,
        List<Map<String, Object>> failures
    ) {
        jdbcTemplate.update(
            """
                update project_space_migration_batches
                   set status = ?, rolled_back_by = ?, rolled_back_at = now(),
                       summary = ?::jsonb, failures = ?::jsonb
                 where workspace_id = ? and id = ?
                """,
            status,
            actorId,
            toJson(summary),
            toJson(failures),
            workspaceId,
            batchId
        );
    }

    @Override
    public void updateSummary(UUID workspaceId, UUID batchId, Map<String, Object> summary) {
        jdbcTemplate.update(
            """
                update project_space_migration_batches
                   set summary = ?::jsonb
                 where workspace_id = ? and id = ?
                """,
            toJson(summary),
            workspaceId,
            batchId
        );
    }

    @Override
    public void lockWorkspaceForMigration(UUID workspaceId) {
        // FOR NO KEY UPDATE (not FOR UPDATE): the per-project REQUIRES_NEW unit transactions insert
        // rows that foreign-key back to workspaces, and those FK checks take FOR KEY SHARE on the
        // workspace row. FOR UPDATE conflicts with FOR KEY SHARE and would self-deadlock against
        // the suspended outer transaction; FOR NO KEY UPDATE still serializes concurrent migration
        // batches of the same workspace without blocking the unit-transaction FK checks.
        jdbcTemplate.queryForList(
            "select id from workspaces where id = ? for no key update",
            UUID.class,
            workspaceId
        );
    }

    private MigrationBatchRecord mapBatch(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MigrationBatchRecord(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            resultSet.getString("status"),
            resultSet.getBoolean("dry_run"),
            instant(resultSet.getTimestamp("source_watermark")),
            resultSet.getString("source_checksum"),
            resultSet.getString("result_checksum"),
            toMap(resultSet.getString("summary")),
            toFailureList(resultSet.getString("failures")),
            resultSet.getObject("started_by", UUID.class),
            resultSet.getTimestamp("started_at").toInstant(),
            instant(resultSet.getTimestamp("finished_at")),
            resultSet.getObject("rolled_back_by", UUID.class),
            instant(resultSet.getTimestamp("rolled_back_at"))
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize migration batch payload", exception);
        }
    }

    private Map<String, Object> toMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, SUMMARY_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read migration batch summary", exception);
        }
    }

    private List<Map<String, Object>> toFailureList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, FAILURES_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read migration batch failures", exception);
        }
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
