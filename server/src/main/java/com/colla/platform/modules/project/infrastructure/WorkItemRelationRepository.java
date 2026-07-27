package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemRelationModels.Direction;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.RelationKind;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationProjection;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.WorkItemRelation;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemRelationRepository {
    void acquireGraphLock(UUID workspaceId, UUID spaceId, String relationKey);

    void insert(NewRelation relation);

    Optional<WorkItemRelation> find(UUID workspaceId, UUID spaceId, UUID relationId, boolean lock);

    Optional<RelationProjection> findProjection(
        UUID workspaceId,
        UUID spaceId,
        UUID relationId
    );

    Optional<WorkItemRelation> findActiveEdge(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        UUID sourceWorkItemId,
        UUID targetWorkItemId
    );

    List<RelationProjection> list(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        String relationKey,
        UUID cursor,
        int limit
    );

    List<WorkItemRelation> listActiveTouching(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId
    );

    long countActiveOutgoing(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        UUID sourceWorkItemId
    );

    long countActiveIncoming(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        UUID targetWorkItemId
    );

    boolean pathExists(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        UUID startWorkItemId,
        UUID soughtWorkItemId,
        int maxDepth
    );

    List<ImpactEdge> listImpact(
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        UUID focusWorkItemId,
        String direction,
        int maxDepth,
        int limit
    );

    int withdraw(
        UUID workspaceId,
        UUID spaceId,
        UUID relationId,
        long expectedVersion,
        UUID actorId,
        String reasonHash
    );

    int restore(
        UUID workspaceId,
        UUID spaceId,
        UUID relationId,
        long expectedVersion,
        UUID actorId
    );

    boolean tryStartCommand(CommandStart command);

    Optional<CommandReceipt> findCommand(
        UUID workspaceId,
        UUID spaceId,
        String operation,
        String requestId
    );

    void completeCommand(
        UUID commandId,
        UUID relationId,
        long relationVersion,
        JsonNode response
    );

    void appendHistory(HistoryAppend history);

    record NewRelation(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        RelationKind kind,
        Direction direction,
        UUID definitionTypeId,
        UUID definitionVersionId,
        String definitionConfigHash,
        UUID sourceWorkItemId,
        UUID targetWorkItemId,
        UUID actorId
    ) {
    }

    record CommandStart(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID relationId,
        String operation,
        String requestId,
        String requestHash,
        UUID actorId
    ) {
    }

    record CommandReceipt(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID relationId,
        String operation,
        String requestId,
        String requestHash,
        String status,
        UUID responseRelationId,
        Long responseRelationVersion,
        JsonNode response,
        UUID createdBy
    ) {
    }

    record HistoryAppend(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID relationId,
        long relationVersion,
        String eventKind,
        String relationKey,
        UUID sourceWorkItemId,
        UUID targetWorkItemId,
        UUID definitionTypeId,
        UUID definitionVersionId,
        String definitionConfigHash,
        UUID commandId,
        JsonNode safeMetadata,
        UUID actorId
    ) {
    }

    record ImpactEdge(
        UUID relationId,
        UUID sourceWorkItemId,
        UUID targetWorkItemId,
        int depth
    ) {
    }
}
