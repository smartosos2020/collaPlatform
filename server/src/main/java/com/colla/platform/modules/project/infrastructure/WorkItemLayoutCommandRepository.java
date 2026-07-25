package com.colla.platform.modules.project.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemLayoutCommandRepository {
    boolean tryStart(CommandStart command);

    Optional<CommandReceipt> find(UUID workspaceId, String requestId);

    void complete(UUID commandId, CommandResponse response);

    record CommandStart(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        String requestId,
        String operation,
        String requestHash,
        UUID actorId
    ) {
    }

    record CommandResponse(
        UUID layoutId,
        long aggregateVersion,
        String configHash,
        String payload
    ) {
    }

    record CommandReceipt(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        String requestId,
        String operation,
        String requestHash,
        String status,
        Integer responseSchemaVersion,
        UUID responseLayoutId,
        Long responseAggregateVersion,
        String responseConfigHash,
        String responsePayload,
        UUID createdBy,
        Instant createdAt,
        Instant completedAt
    ) {
    }
}
