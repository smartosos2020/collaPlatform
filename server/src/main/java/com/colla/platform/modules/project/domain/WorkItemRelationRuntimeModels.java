package com.colla.platform.modules.project.domain;

import com.colla.platform.modules.project.domain.WorkItemRelationModels.Cardinality;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.DeletionPolicy;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.Direction;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.RelationKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkItemRelationRuntimeModels {
    private WorkItemRelationRuntimeModels() {
    }

    public record RelationDefinitionBinding(
        UUID definitionTypeId,
        UUID definitionVersionId,
        String definitionConfigHash,
        String relationKey,
        RelationKind kind,
        Direction direction,
        String forwardName,
        String reverseName,
        List<String> sourceTypeKeys,
        List<String> targetTypeKeys,
        Cardinality sourceCardinality,
        Cardinality targetCardinality,
        DeletionPolicy deletionPolicy,
        boolean allowSelf,
        int maxDepth,
        int sortOrder
    ) {
    }

    public record WorkItemRelation(
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
        String status,
        long version,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        UUID withdrawnBy,
        Instant withdrawnAt
    ) {
    }

    public record RelationEndpoint(
        UUID id,
        UUID typeDefinitionId,
        UUID typeVersionId,
        String typeKey,
        String displayKey,
        String title,
        String status,
        long version
    ) {
    }

    public record RelationProjection(
        WorkItemRelation relation,
        RelationEndpoint source,
        RelationEndpoint target,
        String forwardName,
        String reverseName
    ) {
    }

    public record RelationView(
        UUID id,
        String relationKey,
        String kind,
        String direction,
        String status,
        long version,
        UUID definitionVersionId,
        String definitionConfigHash,
        RelationEndpoint source,
        RelationEndpoint target,
        String perspective,
        String displayName,
        boolean reverse,
        List<String> availableActions,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record RelationPage(List<RelationView> items, UUID nextCursor) {
    }

    public record RelationCapabilities(
        String relationKey,
        boolean visible,
        boolean canCreate,
        boolean canWithdraw,
        boolean canRestore,
        List<String> denialReasons
    ) {
    }
}
