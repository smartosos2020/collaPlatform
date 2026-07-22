package com.colla.platform.modules.project.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemTypeCommandRepository {
    boolean tryStart(CommandStart command);

    Optional<CommandReceipt> find(UUID workspaceId, String requestId);

    void complete(UUID commandId, UUID responseTypeId);

    record CommandStart(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        String requestId,
        String operation,
        String requestHash,
        UUID actorId
    ) {
    }

    record CommandReceipt(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        String requestId,
        String operation,
        String requestHash,
        String status,
        UUID responseTypeId,
        UUID createdBy,
        Instant createdAt,
        Instant completedAt
    ) {
    }
}
