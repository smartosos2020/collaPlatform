package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeBackfillBatch;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeBackfillFailure;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemNodeBackfillRepository {
    boolean tryCreate(BatchCreate value);

    Optional<BatchRecord> findByRequest(UUID workspaceId, UUID spaceId, String requestId);

    Optional<BatchRecord> find(UUID workspaceId, UUID spaceId, UUID batchId);

    void insertUnit(UnitCreate value);

    void markRunning(UUID workspaceId, UUID spaceId, UUID batchId);

    List<UnitRecord> retryableUnits(UUID workspaceId, UUID spaceId, UUID batchId);

    List<UnitRecord> allUnits(UUID workspaceId, UUID spaceId, UUID batchId);

    void markCompleted(
        UUID workspaceId,
        UUID spaceId,
        UUID batchId,
        UUID workItemId,
        long targetWorkItemVersion
    );

    void markFailed(
        UUID workspaceId,
        UUID spaceId,
        UUID batchId,
        UUID workItemId,
        String code,
        String message
    );

    NodeBackfillBatch refreshSummary(UUID workspaceId, UUID spaceId, UUID batchId);

    List<NodeBackfillFailure> failures(UUID workspaceId, UUID spaceId, UUID batchId);

    record BatchCreate(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        UUID targetTypeVersionId,
        String targetConfigHash,
        String targetEntryNodeKey,
        int requestedCount,
        String manifestHash,
        String requestId,
        String requestHash,
        String reasonHash,
        UUID actorId
    ) {
    }

    record BatchRecord(
        NodeBackfillBatch batch,
        String requestHash,
        UUID createdBy
    ) {
    }

    record UnitCreate(
        UUID workspaceId,
        UUID spaceId,
        UUID batchId,
        UUID workItemId,
        UUID sourceTypeVersionId,
        String sourceConfigHash,
        long sourceWorkItemVersion
    ) {
    }

    record UnitRecord(
        UUID workItemId,
        UUID sourceTypeVersionId,
        String sourceConfigHash,
        long sourceWorkItemVersion,
        String status
    ) {
    }
}
