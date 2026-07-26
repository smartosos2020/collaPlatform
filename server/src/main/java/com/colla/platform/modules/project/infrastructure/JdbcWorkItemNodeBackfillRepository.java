package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeBackfillBatch;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeBackfillFailure;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkItemNodeBackfillRepository implements WorkItemNodeBackfillRepository {
    private final JdbcTemplate jdbc;

    public JdbcWorkItemNodeBackfillRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryCreate(BatchCreate value) {
        return jdbc.update(
            """
                insert into project_node_workflow_backfill_batches (
                    id, workspace_id, space_id, type_definition_id, target_type_version_id,
                    target_config_hash, target_entry_node_key, requested_count,
                    manifest_hash, request_id, request_hash, reason_hash, status,
                    created_by, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'planned', ?, now(), now())
                on conflict (workspace_id, space_id, request_id) do nothing
                """,
            value.id(), value.workspaceId(), value.spaceId(), value.typeDefinitionId(),
            value.targetTypeVersionId(), value.targetConfigHash(),
            value.targetEntryNodeKey(), value.requestedCount(), value.manifestHash(),
            value.requestId(), value.requestHash(), value.reasonHash(), value.actorId()
        ) == 1;
    }

    @Override
    public Optional<BatchRecord> findByRequest(
        UUID workspaceId, UUID spaceId, String requestId
    ) {
        return findOne(
            " where workspace_id=? and space_id=? and request_id=?",
            workspaceId, spaceId, requestId
        );
    }

    @Override
    public Optional<BatchRecord> find(UUID workspaceId, UUID spaceId, UUID batchId) {
        return findOne(
            " where workspace_id=? and space_id=? and id=?",
            workspaceId, spaceId, batchId
        );
    }

    private Optional<BatchRecord> findOne(String predicate, Object... arguments) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                """
                    select id, type_definition_id, target_type_version_id, target_config_hash,
                           target_entry_node_key, requested_count, completed_count, failed_count,
                           status, request_hash, created_by
                      from project_node_workflow_backfill_batches
                    """ + predicate,
                (row, number) -> new BatchRecord(
                    new NodeBackfillBatch(
                        row.getObject("id", UUID.class),
                        row.getObject("type_definition_id", UUID.class),
                        row.getObject("target_type_version_id", UUID.class),
                        row.getString("target_config_hash"),
                        row.getString("target_entry_node_key"),
                        row.getInt("requested_count"),
                        row.getInt("completed_count"),
                        row.getInt("failed_count"),
                        row.getString("status")
                    ),
                    row.getString("request_hash"),
                    row.getObject("created_by", UUID.class)
                ),
                arguments
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void insertUnit(UnitCreate value) {
        jdbc.update(
            """
                insert into project_node_workflow_backfill_units (
                    workspace_id, space_id, batch_id, work_item_id,
                    source_type_version_id, source_config_hash, source_work_item_version,
                    status, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'pending', now())
                """,
            value.workspaceId(), value.spaceId(), value.batchId(), value.workItemId(),
            value.sourceTypeVersionId(), value.sourceConfigHash(),
            value.sourceWorkItemVersion()
        );
    }

    @Override
    public void markRunning(UUID workspaceId, UUID spaceId, UUID batchId) {
        jdbc.update(
            """
                update project_node_workflow_backfill_batches
                   set status='running', updated_at=now()
                 where workspace_id=? and space_id=? and id=?
                   and status in ('planned','partial','running')
                """,
            workspaceId, spaceId, batchId
        );
    }

    @Override
    public List<UnitRecord> retryableUnits(UUID workspaceId, UUID spaceId, UUID batchId) {
        return units(workspaceId, spaceId, batchId, " and status in ('pending','failed')");
    }

    @Override
    public List<UnitRecord> allUnits(UUID workspaceId, UUID spaceId, UUID batchId) {
        return units(workspaceId, spaceId, batchId, "");
    }

    private List<UnitRecord> units(
        UUID workspaceId, UUID spaceId, UUID batchId, String statusPredicate
    ) {
        return jdbc.query(
            """
                select work_item_id, source_type_version_id, source_config_hash,
                       source_work_item_version, status
                  from project_node_workflow_backfill_units
                 where workspace_id=? and space_id=? and batch_id=?
                """ + statusPredicate + " order by work_item_id limit 500",
            (row, number) -> new UnitRecord(
                row.getObject("work_item_id", UUID.class),
                row.getObject("source_type_version_id", UUID.class),
                row.getString("source_config_hash"),
                row.getLong("source_work_item_version"),
                row.getString("status")
            ),
            workspaceId, spaceId, batchId
        );
    }

    @Override
    public void markCompleted(
        UUID workspaceId,
        UUID spaceId,
        UUID batchId,
        UUID workItemId,
        long targetWorkItemVersion
    ) {
        jdbc.update(
            """
                update project_node_workflow_backfill_units
                   set status='completed', failure_code=null, failure_message=null,
                       target_work_item_version=?, attempt_count=attempt_count+1,
                       updated_at=now()
                 where workspace_id=? and space_id=? and batch_id=? and work_item_id=?
                """,
            targetWorkItemVersion, workspaceId, spaceId, batchId, workItemId
        );
    }

    @Override
    public void markFailed(
        UUID workspaceId,
        UUID spaceId,
        UUID batchId,
        UUID workItemId,
        String code,
        String message
    ) {
        String safeMessage = message == null ? "Node backfill failed" : message;
        jdbc.update(
            """
                update project_node_workflow_backfill_units
                   set status='failed', failure_code=?, failure_message=?,
                       attempt_count=attempt_count+1, updated_at=now()
                 where workspace_id=? and space_id=? and batch_id=? and work_item_id=?
                """,
            code, safeMessage.substring(0, Math.min(500, safeMessage.length())),
            workspaceId, spaceId, batchId, workItemId
        );
    }

    @Override
    public NodeBackfillBatch refreshSummary(UUID workspaceId, UUID spaceId, UUID batchId) {
        int completed = count(workspaceId, spaceId, batchId, "completed");
        int failed = count(workspaceId, spaceId, batchId, "failed");
        NodeBackfillBatch current = find(workspaceId, spaceId, batchId).orElseThrow().batch();
        String status = completed == current.requestedCount()
            ? "completed" : failed > 0 ? "partial" : "running";
        jdbc.update(
            """
                update project_node_workflow_backfill_batches
                   set completed_count=?, failed_count=?, status=?, updated_at=now()
                 where workspace_id=? and space_id=? and id=?
                """,
            completed, failed, status, workspaceId, spaceId, batchId
        );
        return find(workspaceId, spaceId, batchId).orElseThrow().batch();
    }

    private int count(
        UUID workspaceId, UUID spaceId, UUID batchId, String status
    ) {
        return jdbc.queryForObject(
            """
                select count(*) from project_node_workflow_backfill_units
                 where workspace_id=? and space_id=? and batch_id=? and status=?
                """,
            Integer.class,
            workspaceId, spaceId, batchId, status
        );
    }

    @Override
    public List<NodeBackfillFailure> failures(
        UUID workspaceId, UUID spaceId, UUID batchId
    ) {
        return jdbc.query(
            """
                select work_item_id, failure_code, failure_message
                  from project_node_workflow_backfill_units
                 where workspace_id=? and space_id=? and batch_id=? and status='failed'
                 order by work_item_id limit 500
                """,
            (row, number) -> new NodeBackfillFailure(
                row.getObject("work_item_id", UUID.class),
                row.getString("failure_code"),
                row.getString("failure_message")
            ),
            workspaceId, spaceId, batchId
        );
    }
}
