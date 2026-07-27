package com.colla.platform.modules.platform.infrastructure;

import com.colla.platform.modules.platform.contract.DashboardPersonalization.CardPreference;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DashboardPersonalizationRepository {
    long currentVersion(UUID workspaceId, UUID userId);

    List<CardPreference> layout(UUID workspaceId, UUID userId);

    boolean tryStartCommand(
        UUID id,
        UUID workspaceId,
        UUID userId,
        String operation,
        String requestId,
        String requestHash
    );

    Optional<Long> completedCommand(
        UUID workspaceId,
        UUID userId,
        String operation,
        String requestId,
        String requestHash
    );

    boolean replace(UUID workspaceId, UUID userId, long expectedVersion, long nextVersion, List<CardPreference> cards);

    void completeCommand(UUID id, long responseVersion);

    Instant updatedAt(UUID workspaceId, UUID userId);
}
