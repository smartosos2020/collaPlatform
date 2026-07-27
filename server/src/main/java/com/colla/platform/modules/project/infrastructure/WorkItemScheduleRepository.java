package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineDependency;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineEntry;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineSnapshot;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineSummary;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.TimelineEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemScheduleRepository {
    List<BaselineSummary> listBaselines(
        UUID workspaceId, UUID spaceId, UUID userId, int limit
    );

    Optional<BaselineSnapshot> findBaseline(
        UUID workspaceId, UUID spaceId, UUID userId, UUID baselineId
    );

    Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID userId, String operation, String requestId
    );

    BaselineSnapshot createBaseline(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String requestId,
        String requestHash,
        String responseJson,
        String name,
        String queryHash,
        String bindingJson,
        LocalDate windowStart,
        LocalDate windowEnd,
        Instant expiresAt,
        List<BaselineEntry> entries,
        List<BaselineDependency> dependencies
    );

    BaselineSummary deleteBaseline(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        UUID baselineId,
        String requestId,
        String requestHash,
        long expectedVersion,
        String responseJson
    );

    List<TimelineEvent> timeline(
        UUID workspaceId, UUID spaceId, List<UUID> visibleWorkItemIds, int limit
    );

    void replaceTimelineIndex(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        String viewKey,
        List<TimelineEvent> events
    );

    record CommandRecord(String requestHash, String responseJson) {
    }
}
