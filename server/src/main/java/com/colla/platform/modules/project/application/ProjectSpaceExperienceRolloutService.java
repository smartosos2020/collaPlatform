package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.FALLBACK_CONTEXT;
import static com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.SCHEMA_VERSION;

import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.RolloutState;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.RolloutView;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.TelemetryPolicy;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProjectSpaceExperienceRolloutService {
    private final ProjectSpaceService projectSpaces;
    private final ProjectSpaceExperienceRolloutProperties properties;
    private final ProjectSpaceExperienceMetrics metrics;

    public ProjectSpaceExperienceRolloutService(
        ProjectSpaceService projectSpaces,
        ProjectSpaceExperienceRolloutProperties properties,
        ProjectSpaceExperienceMetrics metrics
    ) {
        this.projectSpaces = projectSpaces;
        this.properties = properties;
        this.metrics = metrics;
    }

    public RolloutView get(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = projectSpaces.getVisible(user, spaceId);
        try {
            return evaluate(user, space);
        } catch (RuntimeException exception) {
            return view("unknown", false, RolloutState.unknown, 0, false, 0, 1);
        }
    }

    private RolloutView evaluate(CurrentUser user, ProjectSpaceSummary space) {
        ProjectSpaceExperienceRolloutProperties.Telemetry telemetry = properties.getTelemetry();
        if (properties.isKillSwitch()) {
            return view(
                properties.getPolicyVersion(),
                false,
                RolloutState.temporarily_disabled,
                0,
                false,
                0,
                telemetry.getMaxBatchSize()
            );
        }
        if (!properties.isEnabled()) {
            return view(
                properties.getPolicyVersion(),
                false,
                RolloutState.baseline,
                properties.getCacheMaxAgeSeconds(),
                false,
                0,
                telemetry.getMaxBatchSize()
            );
        }

        boolean enabled;
        if (matches(
            properties.getExcludedWorkspaceIds(),
            properties.getExcludedSpaceIds(),
            properties.getExcludedUserIds(),
            space.workspaceId(),
            space.id(),
            user.id()
        )) {
            enabled = false;
        } else if (matches(
            properties.getIncludedWorkspaceIds(),
            properties.getIncludedSpaceIds(),
            properties.getIncludedUserIds(),
            space.workspaceId(),
            space.id(),
            user.id()
        )) {
            enabled = true;
        } else {
            int bucket = ProjectSpaceExperienceBucket.stableBucket(
                properties.getEvaluationSalt(),
                properties.getPolicyVersion(),
                space.workspaceId().toString(),
                space.id().toString(),
                user.id().toString()
            );
            enabled = bucket < properties.getRolloutBasisPoints();
        }
        return view(
            properties.getPolicyVersion(),
            enabled,
            enabled ? RolloutState.enabled : RolloutState.baseline,
            properties.getCacheMaxAgeSeconds(),
            telemetry.isEnabled(),
            telemetry.getSampleBasisPoints(),
            telemetry.getMaxBatchSize()
        );
    }

    private boolean matches(
        Set<UUID> workspaceIds,
        Set<UUID> spaceIds,
        Set<UUID> userIds,
        UUID workspaceId,
        UUID spaceId,
        UUID userId
    ) {
        return workspaceIds.contains(workspaceId)
            || spaceIds.contains(spaceId)
            || userIds.contains(userId);
    }

    private RolloutView view(
        String policyVersion,
        boolean enabled,
        RolloutState state,
        int cacheMaxAgeSeconds,
        boolean telemetryEnabled,
        int sampleBasisPoints,
        int maxBatchSize
    ) {
        safeRecord(state);
        return new RolloutView(
            SCHEMA_VERSION,
            policyVersion,
            enabled,
            state,
            FALLBACK_CONTEXT,
            Instant.now(),
            cacheMaxAgeSeconds,
            new TelemetryPolicy(
                SCHEMA_VERSION,
                telemetryEnabled,
                sampleBasisPoints,
                maxBatchSize
            )
        );
    }

    private void safeRecord(RolloutState state) {
        try {
            metrics.recordRollout(state);
        } catch (RuntimeException ignored) {
            // Rollout decisions must remain available when metrics infrastructure is degraded.
        }
    }
}
