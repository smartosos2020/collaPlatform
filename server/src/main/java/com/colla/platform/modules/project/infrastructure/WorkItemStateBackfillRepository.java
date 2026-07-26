package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.StateBackfillBatch;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.StateBackfillFailure;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemStateBackfillRepository {
    boolean tryCreate(BatchCreate batch);

    Optional<BatchRecord> findByRequest(
        UUID workspaceId,
        UUID spaceId,
        String requestId
    );

    Optional<BatchRecord> find(UUID workspaceId, UUID spaceId, UUID batchId);

    void insertUnit(UnitCreate unit);

    List<UnitRecord> retryableUnits(UUID workspaceId, UUID spaceId, UUID batchId);

    List<UnitRecord> allUnits(UUID workspaceId, UUID spaceId, UUID batchId);

    void markRunning(UUID workspaceId, UUID spaceId, UUID batchId);

    void markCompleted(
        UUID workspaceId,
        UUID spaceId,
        UUID batchId,
        UUID workItemId,
        long resultWorkItemVersion
    );

    void markFailed(
        UUID workspaceId,
        UUID spaceId,
        UUID batchId,
        UUID workItemId,
        String errorCode,
        String errorMessage
    );

    StateBackfillBatch refreshSummary(UUID workspaceId, UUID spaceId, UUID batchId);

    List<StateBackfillFailure> failures(UUID workspaceId, UUID spaceId, UUID batchId);

    record BatchCreate(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        UUID targetTypeVersionId,
        String targetConfigHash,
        String targetStateKey,
        int requestedCount,
        String manifestHash,
        String requestId,
        String requestHash,
        String reasonHash,
        UUID actorId
    ) {
    }

    record BatchRecord(
        StateBackfillBatch batch,
        String requestId,
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
        long sourceWorkItemVersion,
        String targetStateKey
    ) {
    }

    record UnitRecord(
        UUID workItemId,
        UUID sourceTypeVersionId,
        String sourceConfigHash,
        long sourceWorkItemVersion,
        String targetStateKey,
        String status
    ) {
    }
}
