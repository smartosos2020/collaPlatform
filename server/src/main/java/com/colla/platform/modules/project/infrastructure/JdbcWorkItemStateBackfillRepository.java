package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.StateBackfillBatch;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.StateBackfillFailure;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkItemStateBackfillRepository implements WorkItemStateBackfillRepository {
    private static final String BATCH_SELECT = """
        select id, space_id, type_definition_id, target_type_version_id,
               target_config_hash, target_state_key, status, requested_count,
               completed_count, failed_count, manifest_hash, request_id,
               request_hash, created_by, created_at, completed_at
          from project_work_item_state_backfill_batches
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkItemStateBackfillRepository(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean tryCreate(BatchCreate value) {
        return jdbcTemplate.update(
            """
                insert into project_work_item_state_backfill_batches (
                    id, workspace_id, space_id, type_definition_id,
                    target_type_version_id, target_config_hash, target_state_key,
                    status, requested_count, manifest_hash, request_id, request_hash,
                    reason_hash, created_by, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'planned', ?, ?, ?, ?, ?, ?, now())
                on conflict (workspace_id, space_id, request_id) do nothing
                """,
            value.id(), value.workspaceId(), value.spaceId(), value.typeDefinitionId(),
            value.targetTypeVersionId(), value.targetConfigHash(), value.targetStateKey(),
            value.requestedCount(), value.manifestHash(), value.requestId(),
            value.requestHash(), value.reasonHash(), value.actorId()
        ) == 1;
    }

    @Override
    public Optional<BatchRecord> findByRequest(
        UUID workspaceId, UUID spaceId, String requestId
    ) {
        return batch(
            BATCH_SELECT + " where workspace_id=? and space_id=? and request_id=?",
            workspaceId, spaceId, requestId
        );
    }

    @Override
    public Optional<BatchRecord> find(UUID workspaceId, UUID spaceId, UUID batchId) {
        return batch(
            BATCH_SELECT + " where workspace_id=? and space_id=? and id=?",
            workspaceId, spaceId, batchId
        );
    }

    @Override
    public void insertUnit(UnitCreate value) {
        jdbcTemplate.update(
            """
                insert into project_work_item_state_backfill_units (
                    workspace_id, space_id, batch_id, work_item_id,
                    source_type_version_id, source_config_hash,
                    source_work_item_version, target_state_key, status,
                    created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, 'pending', now(), now())
                on conflict (workspace_id, space_id, batch_id, work_item_id) do nothing
                """,
            value.workspaceId(), value.spaceId(), value.batchId(), value.workItemId(),
            value.sourceTypeVersionId(), value.sourceConfigHash(),
            value.sourceWorkItemVersion(), value.targetStateKey()
        );
    }

    @Override
    public List<UnitRecord> retryableUnits(
        UUID workspaceId, UUID spaceId, UUID batchId
    ) {
        return units(
            workspaceId, spaceId, batchId,
            " and status in ('pending', 'failed')"
        );
    }

    @Override
    public List<UnitRecord> allUnits(UUID workspaceId, UUID spaceId, UUID batchId) {
        return units(workspaceId, spaceId, batchId, "");
    }

    private List<UnitRecord> units(
        UUID workspaceId, UUID spaceId, UUID batchId, String predicate
    ) {
        return jdbcTemplate.query(
            """
                select work_item_id, source_type_version_id, source_config_hash,
                       source_work_item_version, target_state_key, status
                  from project_work_item_state_backfill_units
                 where workspace_id=? and space_id=? and batch_id=?
                """ + predicate + " order by work_item_id",
            (row, number) -> new UnitRecord(
                row.getObject("work_item_id", UUID.class),
                row.getObject("source_type_version_id", UUID.class),
                row.getString("source_config_hash"),
                row.getLong("source_work_item_version"),
                row.getString("target_state_key"),
                row.getString("status")
            ),
            workspaceId, spaceId, batchId
        );
    }

    @Override
    public void markRunning(UUID workspaceId, UUID spaceId, UUID batchId) {
        jdbcTemplate.update(
            """
                update project_work_item_state_backfill_batches
                   set status='running', started_at=coalesce(started_at, now()),
                       completed_at=null
                 where workspace_id=? and space_id=? and id=?
                """,
            workspaceId, spaceId, batchId
        );
    }

    @Override
    public void markCompleted(
        UUID workspaceId, UUID spaceId, UUID batchId, UUID workItemId,
        long resultWorkItemVersion
    ) {
        jdbcTemplate.update(
            """
                update project_work_item_state_backfill_units
                   set status='completed', attempt_count=attempt_count+1,
                       error_code=null, error_message=null,
                       result_work_item_version=?, updated_at=now()
                 where workspace_id=? and space_id=? and batch_id=? and work_item_id=?
                """,
            resultWorkItemVersion, workspaceId, spaceId, batchId, workItemId
        );
    }

    @Override
    public void markFailed(
        UUID workspaceId, UUID spaceId, UUID batchId, UUID workItemId,
        String errorCode, String errorMessage
    ) {
        jdbcTemplate.update(
            """
                update project_work_item_state_backfill_units
                   set status='failed', attempt_count=attempt_count+1,
                       error_code=?, error_message=?, updated_at=now()
                 where workspace_id=? and space_id=? and batch_id=? and work_item_id=?
                """,
            errorCode, truncate(errorMessage), workspaceId, spaceId, batchId, workItemId
        );
    }

    @Override
    public StateBackfillBatch refreshSummary(
        UUID workspaceId, UUID spaceId, UUID batchId
    ) {
        int completed = count(workspaceId, spaceId, batchId, "completed");
        int failed = count(workspaceId, spaceId, batchId, "failed");
        BatchRecord existing = find(workspaceId, spaceId, batchId).orElseThrow();
        String status = completed == existing.batch().requestedCount()
            ? "completed"
            : failed > 0 ? "partial_failed" : "running";
        jdbcTemplate.update(
            """
                update project_work_item_state_backfill_batches
                   set status=?, completed_count=?, failed_count=?,
                       summary=?::jsonb,
                       completed_at=case when ? in ('completed','partial_failed')
                           then now() else null end
                 where workspace_id=? and space_id=? and id=?
                """,
            status, completed, failed, json(completed, failed), status,
            workspaceId, spaceId, batchId
        );
        return find(workspaceId, spaceId, batchId).orElseThrow().batch();
    }

    @Override
    public List<StateBackfillFailure> failures(
        UUID workspaceId, UUID spaceId, UUID batchId
    ) {
        return jdbcTemplate.query(
            """
                select work_item_id, error_code, error_message
                  from project_work_item_state_backfill_units
                 where workspace_id=? and space_id=? and batch_id=? and status='failed'
                 order by work_item_id
                """,
            (row, number) -> new StateBackfillFailure(
                row.getObject("work_item_id", UUID.class),
                row.getString("error_code"),
                row.getString("error_message")
            ),
            workspaceId, spaceId, batchId
        );
    }

    private Optional<BatchRecord> batch(String sql, Object... arguments) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                sql, this::mapBatch, arguments
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private BatchRecord mapBatch(ResultSet row, int number) throws SQLException {
        var completedAt = row.getTimestamp("completed_at");
        StateBackfillBatch batch = new StateBackfillBatch(
            row.getObject("id", UUID.class),
            row.getObject("space_id", UUID.class),
            row.getObject("type_definition_id", UUID.class),
            row.getObject("target_type_version_id", UUID.class),
            row.getString("target_config_hash"),
            row.getString("target_state_key"),
            row.getString("status"),
            row.getInt("requested_count"),
            row.getInt("completed_count"),
            row.getInt("failed_count"),
            row.getString("manifest_hash"),
            row.getTimestamp("created_at").toInstant(),
            completedAt == null ? null : completedAt.toInstant()
        );
        return new BatchRecord(
            batch, row.getString("request_id"), row.getString("request_hash"),
            row.getObject("created_by", UUID.class)
        );
    }

    private int count(
        UUID workspaceId, UUID spaceId, UUID batchId, String status
    ) {
        return jdbcTemplate.queryForObject(
            """
                select count(*) from project_work_item_state_backfill_units
                 where workspace_id=? and space_id=? and batch_id=? and status=?
                """,
            Integer.class, workspaceId, spaceId, batchId, status
        );
    }

    private String json(int completed, int failed) {
        try {
            return objectMapper.writeValueAsString(
                java.util.Map.of("completed", completed, "failed", failed)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to write backfill summary", exception);
        }
    }

    private String truncate(String value) {
        String safe = value == null ? "State backfill failed" : value;
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }
}
