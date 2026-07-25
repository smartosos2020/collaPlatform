package com.colla.platform.modules.project.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemLayoutCommandRepository {
    boolean tryStart(CommandStart command);

    Optional<CommandReceipt> find(UUID workspaceId, String requestId);

    void complete(UUID commandId, UUID responseLayoutId);

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

    record CommandReceipt(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        String requestId,
        String operation,
        String requestHash,
        String status,
        UUID responseLayoutId,
        UUID createdBy,
        Instant createdAt,
        Instant completedAt
    ) {
    }
}
