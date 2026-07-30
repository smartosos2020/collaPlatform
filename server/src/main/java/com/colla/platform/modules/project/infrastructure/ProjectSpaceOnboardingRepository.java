package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingMutation;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.OnboardingState;
import com.colla.platform.modules.project.domain.ProjectSpaceOnboardingModels.TelemetryEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectSpaceOnboardingRepository {
    Optional<OnboardingState> find(UUID workspaceId, UUID spaceId, UUID userId);

    OnboardingState save(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        OnboardingMutation mutation,
        long expectedVersion
    );

    int appendTelemetry(UUID workspaceId, UUID spaceId, List<TelemetryEvent> events);

    int purgeExpiredTelemetry(int limit);
}
